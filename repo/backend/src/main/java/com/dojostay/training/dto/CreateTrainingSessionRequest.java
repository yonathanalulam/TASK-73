package com.dojostay.training.dto;

import com.dojostay.training.TrainingSession;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Create-session payload. Phase B6 adds sparring matching filters:
 * session type (venue/online), online URL, skill level, weight class, and
 * style. Times must fall on 30-minute boundaries — the service rejects any
 * start/end that is not aligned, so the UI can pre-round on the client and
 * the DB never contains awkward 17-minute sessions.
 */
public record CreateTrainingSessionRequest(
        @NotNull Long trainingClassId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Size(max = 128) String location,
        Long instructorUserId,
        @Positive Integer capacity,
        @Size(max = 512) String notes,
        TrainingSession.SessionType sessionType,
        @Size(max = 512) String onlineUrl,
        @Size(max = 32) String level,
        @Positive Integer weightClassLbs,
        @Size(max = 64) String style
) {
}
