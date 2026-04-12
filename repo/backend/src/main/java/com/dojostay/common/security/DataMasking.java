package com.dojostay.common.security;

/**
 * Display-time masking helpers for sensitive fields. The encryption converter
 * ({@link SensitiveStringConverter}) protects data at rest; these helpers
 * protect it at read time in API responses, where even an authorized caller
 * should only see a redacted form unless they explicitly hold a "view raw"
 * permission (see {@code StudentService.toResponse} for the call site).
 *
 * <p>Design intent: default-masked so any new DTO accidentally reusing
 * a sensitive field does not leak it. A separate unmasked path has to be
 * opted into explicitly.
 */
public final class DataMasking {

    private DataMasking() {}

    /**
     * Mask a phone number, preserving the last 4 digits only. {@code null} and
     * short inputs return an empty-ish placeholder so callers never display a
     * raw short value.
     */
    public static String maskPhone(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 4) return "***";
        return "***-***-" + digits.substring(digits.length() - 4);
    }

    /**
     * Mask an email address: keep the first character of the local part, mask
     * the rest, keep the full domain so users can still identify which provider
     * the account uses ({@code a***@example.com}).
     */
    public static String maskEmail(String raw) {
        if (raw == null) return null;
        int at = raw.indexOf('@');
        if (at <= 1) return "***";
        return raw.charAt(0) + "***" + raw.substring(at);
    }

    /**
     * Mask an identifier-like string (external student id, document number):
     * reveal first 2 and last 2 chars, hide the middle.
     */
    public static String maskId(String raw) {
        if (raw == null) return null;
        if (raw.length() <= 4) return "***";
        return raw.substring(0, 2) + "***" + raw.substring(raw.length() - 2);
    }
}
