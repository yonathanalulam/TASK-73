package com.dojostay.ops;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ExportJobRepository
        extends JpaRepository<ExportJob, Long>, JpaSpecificationExecutor<ExportJob> {

    List<ExportJob> findByRequestedByUserIdOrderByIdDesc(Long requestedByUserId);
}
