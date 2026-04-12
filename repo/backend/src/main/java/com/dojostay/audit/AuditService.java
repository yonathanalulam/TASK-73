package com.dojostay.audit;

import com.dojostay.common.CorrelationIdHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Append-only audit writer.
 *
 * <p>Calls intentionally use {@code REQUIRES_NEW} so an audit entry is recorded even when
 * the surrounding business transaction rolls back (e.g. failed login attempts must still
 * be logged). This is critical for the immutability guarantees in the spec.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action,
                       Long actorUserId,
                       String actorUsername,
                       String targetType,
                       String targetId,
                       String summary,
                       String sourceIp) {
        AuditLog row = new AuditLog();
        row.setAction(action.name());
        row.setActorUserId(actorUserId);
        row.setActorUsername(actorUsername);
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setSummary(summary);
        row.setCorrelationId(CorrelationIdHolder.get());
        row.setSourceIp(sourceIp);
        row.setOccurredAt(Instant.now());
        repository.save(row);
    }
}
