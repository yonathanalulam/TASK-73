package com.dojostay.community;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.common.exception.NotFoundException;
import com.dojostay.community.dto.CreateModerationReportRequest;
import com.dojostay.community.dto.ModerationReportResponse;
import com.dojostay.community.dto.ResolveReportRequest;
import com.dojostay.scopes.DataScopeService;
import com.dojostay.scopes.EffectiveScope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Moderation queue. Users with {@code community.write} can file a report
 * against a post or comment they can read; users with {@code moderation.review}
 * can list and resolve the reports within their data scope. Resolving a report
 * as {@code UPHELD} optionally hides the target.
 */
@Service
public class ModerationService {

    private final ModerationReportRepository reportRepository;
    private final CommunityService communityService;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;

    public ModerationService(ModerationReportRepository reportRepository,
                             CommunityService communityService,
                             DataScopeService dataScopeService,
                             AuditService auditService) {
        this.reportRepository = reportRepository;
        this.communityService = communityService;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ModerationReportResponse> listOpen() {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        return reportRepository.findAll().stream()
                .filter(r -> r.getStatus() == ModerationReport.Status.OPEN)
                .filter(r -> scope.fullAccess() || scope.hasOrganization(r.getOrganizationId()))
                .map(ModerationService::toResponse)
                .toList();
    }

    @Transactional
    public ModerationReportResponse file(CreateModerationReportRequest req, String sourceIp) {
        Long actorId = requireActor();
        Long organizationId;
        switch (req.targetType()) {
            case POST -> {
                Post p = communityService.loadPostForModeration(req.targetId());
                organizationId = p.getOrganizationId();
            }
            case COMMENT -> {
                PostComment c = communityService.loadCommentForModeration(req.targetId());
                organizationId = c.getOrganizationId();
            }
            default -> throw new BusinessException("UNSUPPORTED_TARGET",
                    "Unknown moderation target type",
                    HttpStatus.BAD_REQUEST);
        }
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!scope.fullAccess() && !scope.hasOrganization(organizationId)) {
            // Scope hides existence.
            throw new NotFoundException("Target not found");
        }

        ModerationReport r = new ModerationReport();
        r.setOrganizationId(organizationId);
        r.setReporterUserId(actorId);
        r.setTargetType(req.targetType());
        r.setTargetId(req.targetId());
        r.setReason(req.reason());
        r.setDetails(req.details());
        r.setStatus(ModerationReport.Status.OPEN);
        ModerationReport saved = reportRepository.save(r);

        auditService.record(AuditAction.MODERATION_REPORT_FILED, actorId(), actorUsername(),
                req.targetType().name(), String.valueOf(req.targetId()),
                "Reason: " + req.reason(), sourceIp);
        return toResponse(saved);
    }

    @Transactional
    public ModerationReportResponse resolve(Long reportId, ResolveReportRequest req, String sourceIp) {
        ModerationReport r = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!scope.fullAccess() && !scope.hasOrganization(r.getOrganizationId())) {
            throw new NotFoundException("Report not found");
        }
        if (r.getStatus() != ModerationReport.Status.OPEN) {
            throw new BusinessException("REPORT_ALREADY_RESOLVED",
                    "Report already resolved", HttpStatus.CONFLICT);
        }
        if (req.resolution() == ModerationReport.Status.OPEN) {
            throw new BusinessException("INVALID_RESOLUTION",
                    "Resolution must be UPHELD or DISMISSED",
                    HttpStatus.BAD_REQUEST);
        }

        r.setStatus(req.resolution());
        r.setResolutionNotes(req.resolutionNotes());
        r.setReviewedByUserId(requireActor());
        r.setResolvedAt(Instant.now());
        reportRepository.save(r);

        if (req.resolution() == ModerationReport.Status.UPHELD && req.hideTarget()) {
            String reason = req.hiddenReason() != null ? req.hiddenReason() : req.resolutionNotes();
            switch (r.getTargetType()) {
                case POST -> {
                    Post p = communityService.loadPostForModeration(r.getTargetId());
                    communityService.hidePost(p, reason);
                    auditService.record(AuditAction.POST_HIDDEN, actorId(), actorUsername(),
                            "POST", String.valueOf(p.getId()),
                            "Hidden via moderation report " + r.getId(), sourceIp);
                }
                case COMMENT -> {
                    PostComment c = communityService.loadCommentForModeration(r.getTargetId());
                    communityService.hideComment(c, reason);
                    auditService.record(AuditAction.COMMENT_HIDDEN, actorId(), actorUsername(),
                            "COMMENT", String.valueOf(c.getId()),
                            "Hidden via moderation report " + r.getId(), sourceIp);
                }
            }
        }

        auditService.record(AuditAction.MODERATION_REPORT_RESOLVED, actorId(), actorUsername(),
                "MODERATION_REPORT", String.valueOf(r.getId()),
                "Resolution: " + req.resolution(), sourceIp);
        return toResponse(r);
    }

    /**
     * Restore a previously hidden post or comment back to PUBLISHED.
     * The {@code restored_at} timestamp is recorded so the moderation trail
     * shows the item was once hidden and then reinstated. Can only restore
     * items currently in {@code HIDDEN} status — DELETED items are irrecoverable.
     */
    @Transactional
    public void restore(ModerationReport.TargetType targetType, Long targetId, String sourceIp) {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        switch (targetType) {
            case POST -> {
                Post p = communityService.loadPostForModeration(targetId);
                if (!scope.fullAccess() && !scope.hasOrganization(p.getOrganizationId())) {
                    throw new NotFoundException("Post not found");
                }
                if (p.getStatus() != Post.Status.HIDDEN) {
                    throw new BusinessException("NOT_HIDDEN",
                            "Only HIDDEN posts can be restored", HttpStatus.CONFLICT);
                }
                communityService.restorePost(p);
                auditService.record(AuditAction.POST_RESTORED, actorId(), actorUsername(),
                        "POST", String.valueOf(p.getId()),
                        "Post restored to PUBLISHED", sourceIp);
            }
            case COMMENT -> {
                PostComment c = communityService.loadCommentForModeration(targetId);
                if (!scope.fullAccess() && !scope.hasOrganization(c.getOrganizationId())) {
                    throw new NotFoundException("Comment not found");
                }
                if (c.getStatus() != PostComment.Status.HIDDEN) {
                    throw new BusinessException("NOT_HIDDEN",
                            "Only HIDDEN comments can be restored", HttpStatus.CONFLICT);
                }
                communityService.restoreComment(c);
                auditService.record(AuditAction.COMMENT_RESTORED, actorId(), actorUsername(),
                        "COMMENT", String.valueOf(c.getId()),
                        "Comment restored to PUBLISHED", sourceIp);
            }
        }
    }

    private static Long actorId() {
        return CurrentUserResolver.current().map(CurrentUser::id).orElse(null);
    }

    private static String actorUsername() {
        return CurrentUserResolver.current().map(CurrentUser::username).orElse("system");
    }

    private static Long requireActor() {
        Long id = actorId();
        if (id == null) {
            throw new BusinessException("NO_ACTOR",
                    "An authenticated user is required",
                    HttpStatus.UNAUTHORIZED);
        }
        return id;
    }

    private static ModerationReportResponse toResponse(ModerationReport r) {
        return new ModerationReportResponse(
                r.getId(), r.getOrganizationId(), r.getReporterUserId(),
                r.getTargetType(), r.getTargetId(), r.getReason(), r.getDetails(),
                r.getStatus(), r.getResolutionNotes(), r.getReviewedByUserId(),
                r.getCreatedAt(), r.getResolvedAt()
        );
    }
}
