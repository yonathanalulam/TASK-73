package com.dojostay.ops;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.ops.dto.FeatureToggleResponse;
import com.dojostay.ops.dto.UpsertFeatureToggleRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Feature toggle registry. Toggles are a global, admin-managed concept — they
 * have no org scope, so there's no {@code DataScopeSpec} filtering here.
 * Mutations are audited with {@link AuditAction#FEATURE_TOGGLE_CHANGED}.
 */
@Service
public class FeatureToggleService {

    private final FeatureToggleRepository repository;
    private final AuditService auditService;

    public FeatureToggleService(FeatureToggleRepository repository,
                                AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<FeatureToggleResponse> list() {
        return repository.findAll().stream()
                .map(FeatureToggleService::toResponse)
                .toList();
    }

    @Transactional
    public FeatureToggleResponse upsert(UpsertFeatureToggleRequest req, String sourceIp) {
        FeatureToggle t = repository.findByCode(req.code()).orElseGet(FeatureToggle::new);
        boolean isNew = t.getId() == null;
        boolean prev = t.isEnabled();
        t.setCode(req.code());
        t.setDescription(req.description());
        t.setEnabled(Boolean.TRUE.equals(req.enabled()));
        t.setUpdatedByUserId(actorId());
        FeatureToggle saved = repository.save(t);

        if (isNew || prev != saved.isEnabled()) {
            auditService.record(AuditAction.FEATURE_TOGGLE_CHANGED, actorId(), actorUsername(),
                    "FEATURE_TOGGLE", saved.getCode(),
                    (isNew ? "Created " : "Changed ")
                            + saved.getCode() + " enabled=" + saved.isEnabled(),
                    sourceIp);
        }
        return toResponse(saved);
    }

    /** Best-effort enabled check used by other services. Falls back to false. */
    @Transactional(readOnly = true)
    public boolean isEnabled(String code) {
        return repository.findByCode(code).map(FeatureToggle::isEnabled).orElse(false);
    }

    private static Long actorId() {
        return CurrentUserResolver.current().map(CurrentUser::id).orElse(null);
    }

    private static String actorUsername() {
        return CurrentUserResolver.current().map(CurrentUser::username).orElse("system");
    }

    private static FeatureToggleResponse toResponse(FeatureToggle t) {
        return new FeatureToggleResponse(
                t.getId(), t.getCode(), t.getDescription(),
                t.isEnabled(), t.getUpdatedAt(), t.getUpdatedByUserId()
        );
    }
}
