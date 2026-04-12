package com.dojostay.property;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {
    List<RoomType> findByPropertyIdOrderByCodeAsc(Long propertyId);
    Optional<RoomType> findByPropertyIdAndCode(Long propertyId, String code);
}
