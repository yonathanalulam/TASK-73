package com.dojostay.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface TrainingClassRepository
        extends JpaRepository<TrainingClass, Long>, JpaSpecificationExecutor<TrainingClass> {

    Optional<TrainingClass> findByOrganizationIdAndCode(Long organizationId, String code);

    List<TrainingClass> findByOrganizationId(Long organizationId);
}
