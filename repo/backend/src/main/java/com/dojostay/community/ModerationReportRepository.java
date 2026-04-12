package com.dojostay.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ModerationReportRepository
        extends JpaRepository<ModerationReport, Long>, JpaSpecificationExecutor<ModerationReport> {
}
