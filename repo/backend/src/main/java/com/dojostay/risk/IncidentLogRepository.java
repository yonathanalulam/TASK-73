package com.dojostay.risk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IncidentLogRepository
        extends JpaRepository<IncidentLog, Long>, JpaSpecificationExecutor<IncidentLog> {
}
