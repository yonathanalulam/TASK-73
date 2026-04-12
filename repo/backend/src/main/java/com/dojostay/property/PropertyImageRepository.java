package com.dojostay.property;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropertyImageRepository extends JpaRepository<PropertyImage, Long> {
    List<PropertyImage> findByPropertyIdOrderByDisplayOrderAsc(Long propertyId);
}
