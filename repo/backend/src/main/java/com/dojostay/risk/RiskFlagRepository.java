package com.dojostay.risk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RiskFlagRepository
        extends JpaRepository<RiskFlag, Long>, JpaSpecificationExecutor<RiskFlag> {
}
