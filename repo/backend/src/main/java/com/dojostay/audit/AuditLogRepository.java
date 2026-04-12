package com.dojostay.audit;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Note: only insert/find are intended. There is no service-level update or delete API for
 * audit rows; the table is treated as append-only.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
