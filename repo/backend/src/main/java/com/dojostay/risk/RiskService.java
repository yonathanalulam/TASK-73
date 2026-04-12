package com.dojostay.risk;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.common.exception.NotFoundException;
import com.dojostay.risk.dto.ClearRiskFlagRequest;
import com.dojostay.risk.dto.IncidentResponse;
import com.dojostay.risk.dto.LogIncidentRequest;
import com.dojostay.risk.dto.RaiseRiskFlagRequest;
import com.dojostay.risk.dto.RiskFlagResponse;
import com.dojostay.scopes.DataScopeService;
import com.dojostay.scopes.EffectiveScope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Risk flag registry + incident log. Risk flags are mutable only via
 * {@link #clear}; everything else is append-only. Incidents are permanently
 * append-only: corrections are logged as new incidents that reference the
 * original in {@link LogIncidentRequest#followUp}.
 */
@Service
public class RiskService {

    private final RiskFlagRepository riskRepository;
    private final IncidentLogRepository incidentRepository;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;

    public RiskService(RiskFlagRepository riskRepository,
                       IncidentLogRepository incidentRepository,
                       DataScopeService dataScopeService,
                       AuditService auditService) {
        this.riskRepository = riskRepository;
        this.incidentRepository = incidentRepository;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<RiskFlagResponse> listFlags() {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        return riskRepository.findAll().stream()
                .filter(r -> scope.fullAccess() || scope.hasOrganization(r.getOrganizationId()))
                .map(RiskService::toResponse)
                .toList();
    }

    @Transactional
    public RiskFlagResponse raiseFlag(RaiseRiskFlagRequest req, String sourceIp) {
        assertOrgAccessibleWrite(req.organizationId());
        RiskFlag f = new RiskFlag();
        f.setOrganizationId(req.organizationId());
        f.setSubjectType(req.subjectType());
        f.setSubjectId(req.subjectId());
        f.setCategory(req.category());
        f.setSeverity(req.severity());
        f.setDescription(req.description());
        f.setStatus(RiskFlag.Status.OPEN);
        f.setRaisedByUserId(requireActor());
        RiskFlag saved = riskRepository.save(f);

        auditService.record(AuditAction.RISK_FLAG_RAISED, actorId(), actorUsername(),
                req.subjectType().name(), String.valueOf(req.subjectId()),
                req.severity() + "/" + req.category() + " — " + req.description(),
                sourceIp);
        return toResponse(saved);
    }

    @Transactional
    public RiskFlagResponse clearFlag(Long flagId, ClearRiskFlagRequest req, String sourceIp) {
        RiskFlag f = riskRepository.findById(flagId)
                .orElseThrow(() -> new NotFoundException("Risk flag not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!scope.fullAccess() && !scope.hasOrganization(f.getOrganizationId())) {
            throw new NotFoundException("Risk flag not found");
        }
        if (f.getStatus() == RiskFlag.Status.CLEARED) {
            throw new BusinessException("ALREADY_CLEARED",
                    "Risk flag already cleared", HttpStatus.CONFLICT);
        }
        f.setStatus(RiskFlag.Status.CLEARED);
        f.setClearanceNotes(req.clearanceNotes());
        f.setClearedByUserId(requireActor());
        f.setClearedAt(Instant.now());
        RiskFlag saved = riskRepository.save(f);

        auditService.record(AuditAction.RISK_FLAG_CLEARED, actorId(), actorUsername(),
                "RISK_FLAG", String.valueOf(saved.getId()),
                "Cleared: " + req.clearanceNotes(), sourceIp);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> listIncidents() {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        return incidentRepository.findAll().stream()
                .filter(i -> scope.fullAccess() || scope.hasOrganization(i.getOrganizationId()))
                .map(RiskService::toResponse)
                .toList();
    }

    @Transactional
    public IncidentResponse logIncident(LogIncidentRequest req, String sourceIp) {
        assertOrgAccessibleWrite(req.organizationId());
        IncidentLog i = new IncidentLog();
        i.setOrganizationId(req.organizationId());
        i.setOccurredAt(req.occurredAt());
        i.setReporterUserId(requireActor());
        i.setSubjectType(req.subjectType());
        i.setSubjectId(req.subjectId());
        i.setCategory(req.category());
        i.setSeverity(req.severity());
        i.setDescription(req.description());
        i.setFollowUp(req.followUp());
        IncidentLog saved = incidentRepository.save(i);

        auditService.record(AuditAction.INCIDENT_LOGGED, actorId(), actorUsername(),
                "INCIDENT", String.valueOf(saved.getId()),
                req.severity() + "/" + req.category(),
                sourceIp);
        return toResponse(saved);
    }

    private void assertOrgAccessibleWrite(Long organizationId) {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (scope.fullAccess()) return;
        if (organizationId == null || !scope.hasOrganization(organizationId)) {
            throw new BusinessException("OUT_OF_SCOPE",
                    "Target organization is not in your data scope",
                    HttpStatus.FORBIDDEN);
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

    private static RiskFlagResponse toResponse(RiskFlag f) {
        return new RiskFlagResponse(
                f.getId(), f.getOrganizationId(),
                f.getSubjectType(), f.getSubjectId(),
                f.getCategory(), f.getSeverity(), f.getDescription(),
                f.getStatus(), f.getRaisedByUserId(), f.getClearedByUserId(),
                f.getClearanceNotes(), f.getCreatedAt(), f.getClearedAt()
        );
    }

    private static IncidentResponse toResponse(IncidentLog i) {
        return new IncidentResponse(
                i.getId(), i.getOrganizationId(), i.getOccurredAt(),
                i.getReporterUserId(), i.getSubjectType(), i.getSubjectId(),
                i.getCategory(), i.getSeverity(), i.getDescription(),
                i.getFollowUp(), i.getCreatedAt()
        );
    }
}
