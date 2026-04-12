package com.dojostay.students;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface StudentRepository
        extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    Optional<Student> findByOrganizationIdAndExternalId(Long organizationId, String externalId);

    Optional<Student> findByUserId(Long userId);
}
