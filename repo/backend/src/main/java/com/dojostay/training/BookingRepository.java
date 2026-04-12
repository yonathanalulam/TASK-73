package com.dojostay.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BookingRepository
        extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    List<Booking> findByTrainingSessionId(Long trainingSessionId);

    List<Booking> findByStudentId(Long studentId);

    /**
     * Returns non-terminal bookings for this student whose parent session's
     * time window overlaps {@code [startsAt, endsAt)}. Used to reject double
     * bookings across concurrent sessions. The join on {@code TrainingSession}
     * is the only way to compare times, since bookings themselves don't carry
     * the window.
     *
     * <p>Phase B6 introduces new lifecycle states: {@code CANCELED} and
     * {@code REFUNDED} also release seats, so they are excluded here in
     * addition to the legacy {@code CANCELLED} / {@code NO_SHOW}.
     */
    @Query("""
            select b from Booking b, TrainingSession s
            where b.trainingSessionId = s.id
              and b.studentId = :studentId
              and b.status <> com.dojostay.training.Booking$Status.CANCELLED
              and b.status <> com.dojostay.training.Booking$Status.CANCELED
              and b.status <> com.dojostay.training.Booking$Status.REFUNDED
              and b.status <> com.dojostay.training.Booking$Status.NO_SHOW
              and s.startsAt < :endsAt
              and s.endsAt > :startsAt
            """)
    List<Booking> findConflictingBookings(@Param("studentId") Long studentId,
                                          @Param("startsAt") Instant startsAt,
                                          @Param("endsAt") Instant endsAt);

    /**
     * Counts seats currently held on a session — used for capacity checks.
     * {@code CANCELED} and {@code REFUNDED} (new B6 states) release seats
     * alongside the legacy {@code CANCELLED} / {@code NO_SHOW}.
     */
    @Query("""
            select count(b) from Booking b
            where b.trainingSessionId = :sessionId
              and b.status <> com.dojostay.training.Booking$Status.CANCELLED
              and b.status <> com.dojostay.training.Booking$Status.CANCELED
              and b.status <> com.dojostay.training.Booking$Status.REFUNDED
              and b.status <> com.dojostay.training.Booking$Status.NO_SHOW
            """)
    long countActiveBookingsForSession(@Param("sessionId") Long sessionId);
}
