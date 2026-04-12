package com.dojostay.organizations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacilityAreaRepository extends JpaRepository<FacilityArea, Long> {

    List<FacilityArea> findByOrganizationId(Long organizationId);

    List<FacilityArea> findByDepartmentId(Long departmentId);
}
