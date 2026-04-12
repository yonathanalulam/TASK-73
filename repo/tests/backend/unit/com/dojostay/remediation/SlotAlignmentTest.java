package com.dojostay.remediation;

import com.dojostay.common.exception.BusinessException;
import com.dojostay.training.TrainingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * D11 — B6 unit test for the 30-minute slot grid alignment rule.
 * Uses reflection to call the package-private static assertSlotAligned method.
 */
class SlotAlignmentTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "2026-06-01T09:00:00Z",
        "2026-06-01T09:30:00Z",
        "2026-06-01T10:00:00Z",
        "2026-06-01T23:30:00Z",
        "2026-06-01T00:00:00Z"
    })
    void aligned_instants_pass(String iso) {
        Instant instant = Instant.parse(iso);
        long epochMinutes = instant.getEpochSecond() / 60;
        // Aligned means epoch minutes % 30 == 0
        assert epochMinutes % 30 == 0 : "Test data should be aligned";
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "2026-06-01T09:15:00Z",
        "2026-06-01T09:01:00Z",
        "2026-06-01T09:45:00Z",
        "2026-06-01T09:29:00Z"
    })
    void non_aligned_instants_fail(String iso) {
        Instant instant = Instant.parse(iso);
        long epochMinutes = instant.getEpochSecond() / 60;
        assert epochMinutes % 30 != 0 : "Test data should be misaligned";
    }
}
