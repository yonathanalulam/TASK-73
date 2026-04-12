package com.dojostay.property;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface LodgingReservationRepository
        extends JpaRepository<LodgingReservation, Long>, JpaSpecificationExecutor<LodgingReservation> {

    /**
     * Finds bed ids that have any non-cancelled reservation overlapping the given
     * window. Two intervals [a,b) and [c,d) overlap when {@code a < d AND c < b}.
     * Reservation dates are inclusive-start / exclusive-end to match lodging semantics.
     */
    @Query("""
            select r.bedId from LodgingReservation r
            where r.status <> com.dojostay.property.LodgingReservation$Status.CANCELLED
              and r.bedId in :bedIds
              and r.startsOn < :endsOn
              and r.endsOn > :startsOn
            """)
    List<Long> findBookedBedIdsInRange(
            @Param("bedIds") Set<Long> bedIds,
            @Param("startsOn") LocalDate startsOn,
            @Param("endsOn") LocalDate endsOn
    );
}
