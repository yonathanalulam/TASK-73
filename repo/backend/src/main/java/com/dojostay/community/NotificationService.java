package com.dojostay.community;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.common.exception.NotFoundException;
import com.dojostay.community.dto.NotificationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Notifications are scoped to a single recipient user. Every user can only
 * read and mark-read their own notifications. The service also exposes an
 * internal {@link #deliver} entry point for other services to emit
 * notifications as a side effect of domain events.
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final AuditService auditService;

    public NotificationService(NotificationRepository repository,
                               AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMine() {
        Long userId = requireActor();
        return repository.findByRecipientUserIdOrderByIdDesc(userId).stream()
                .map(NotificationService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByRecipientUserIdAndReadAtIsNull(requireActor());
    }

    @Transactional
    public NotificationResponse markRead(Long id) {
        Notification n = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        if (!n.getRecipientUserId().equals(requireActor())) {
            // hide existence from non-recipients
            throw new NotFoundException("Notification not found");
        }
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
            repository.save(n);
        }
        return toResponse(n);
    }

    /**
     * Invoked by other services to deliver a notification. Emits a
     * {@link AuditAction#NOTIFICATION_SENT} audit event so the delivery is
     * traceable even though we don't persist a separate delivery log.
     */
    @Transactional
    public Notification deliver(Long recipientUserId, Long organizationId, String kind,
                                String title, String body,
                                String referenceType, String referenceId,
                                String sourceIp) {
        Notification n = new Notification();
        n.setRecipientUserId(recipientUserId);
        n.setOrganizationId(organizationId);
        n.setKind(kind);
        n.setTitle(title);
        n.setBody(body);
        n.setReferenceType(referenceType);
        n.setReferenceId(referenceId);
        Notification saved = repository.save(n);
        auditService.record(AuditAction.NOTIFICATION_SENT, actorId(), actorUsername(),
                "NOTIFICATION", String.valueOf(saved.getId()),
                kind + " -> user " + recipientUserId, sourceIp);
        return saved;
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

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getRecipientUserId(), n.getOrganizationId(),
                n.getKind(), n.getTitle(), n.getBody(),
                n.getReferenceType(), n.getReferenceId(),
                n.getReadAt(), n.getCreatedAt()
        );
    }
}
