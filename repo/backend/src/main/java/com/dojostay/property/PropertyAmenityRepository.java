package com.dojostay.property;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropertyAmenityRepository extends JpaRepository<PropertyAmenity, Long> {
    List<PropertyAmenity> findByPropertyIdOrderByCodeAsc(Long propertyId);
    void deleteByPropertyIdAndCode(Long propertyId, String code);
}
