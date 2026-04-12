package com.dojostay.training.dto;

import com.dojostay.training.TrainingSession;

import java.time.Instant;

public record TrainingSessionResponse(
        Long id,
        Long trainingClassId,
        Long organizationId,
        Instant startsAt,
        Instant endsAt,
        String location,
        Long instructorUserId,
        int capacity,
        long bookedSeats,
        TrainingSession.Status status,
        String notes,
        TrainingSession.SessionType sessionType,
        String onlineUrl,
        String level,
        Integer weightClassLbs,
        String style,
        Instant createdAt,
        Instant cancelledAt
) {
}
