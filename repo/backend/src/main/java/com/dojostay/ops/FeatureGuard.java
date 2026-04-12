package com.dojostay.ops;

import com.dojostay.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Centralized toggle enforcement. Domain services call
 * {@link #requireEnabled(String, String)} before executing mutations governed
 * by a feature toggle. When the toggle is disabled, a consistent
 * {@code FEATURE_DISABLED} error is returned.
 *
 * <p>Well-known toggle codes:
 * <ul>
 *   <li>{@code community.enabled} — gates all community write operations</li>
 *   <li>{@code bookings.read-only} — when enabled, blocks booking mutations</li>
 *   <li>{@code property.read-only} — when enabled, blocks property mutations</li>
 * </ul>
 */
@Component
public class FeatureGuard {

    /** Toggle codes used across the application. */
    public static final String COMMUNITY_ENABLED = "community.enabled";
    public static final String BOOKINGS_READ_ONLY = "bookings.read-only";
    public static final String PROPERTY_READ_ONLY = "property.read-only";

    private final FeatureToggleService toggleService;

    public FeatureGuard(FeatureToggleService toggleService) {
        this.toggleService = toggleService;
    }

    /**
     * Require that the named toggle is enabled. Throws a consistent error if
     * the toggle exists and is disabled.
     *
     * @param toggleCode the toggle code to check
     * @param action human-readable description of the blocked action
     */
    public void requireEnabled(String toggleCode, String action) {
        if (!toggleService.isEnabled(toggleCode)) {
            throw new BusinessException("FEATURE_DISABLED",
                    action + " is currently disabled by feature toggle '" + toggleCode + "'",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * Require that the named toggle is NOT enabled (i.e., read-only mode is off).
     * Used for "read-only" toggles where enabled=true means writes are blocked.
     *
     * @param toggleCode the toggle code to check
     * @param action human-readable description of the blocked action
     */
    public void requireNotReadOnly(String toggleCode, String action) {
        if (toggleService.isEnabled(toggleCode)) {
            throw new BusinessException("FEATURE_DISABLED",
                    action + " is currently in read-only mode (toggle '" + toggleCode + "' is active)",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
