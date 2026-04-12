package com.dojostay.property;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NightlyRateRepository extends JpaRepository<NightlyRate, Long> {

    List<NightlyRate> findByRoomTypeIdAndStayDateBetweenOrderByStayDateAsc(
            Long roomTypeId, LocalDate from, LocalDate to);

    Optional<NightlyRate> findByRoomTypeIdAndStayDate(Long roomTypeId, LocalDate stayDate);
}
