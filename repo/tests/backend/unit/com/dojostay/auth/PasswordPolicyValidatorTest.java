package com.dojostay.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyValidatorTest {

    private PasswordPolicyValidator validator;

    @BeforeEach
    void setUp() {
        // Default policy mirrors application.yml: 12 chars, upper, lower, digit, special.
        PasswordPolicyProperties props = new PasswordPolicyProperties();
        validator = new PasswordPolicyValidator(props);
    }

    @Test
    void rejects_null() {
        assertFalse(validator.validate(null).valid());
    }

    @Test
    void rejects_too_short() {
        assertFalse(validator.validate("Aa1!aaa").valid());
    }

    @Test
    void rejects_missing_uppercase() {
        assertFalse(validator.validate("abcdefghi1!@").valid());
    }

    @Test
    void rejects_missing_lowercase() {
        assertFalse(validator.validate("ABCDEFGHI1!@").valid());
    }

    @Test
    void rejects_missing_digit() {
        assertFalse(validator.validate("Abcdefghij!@").valid());
    }

    @Test
    void rejects_missing_special() {
        assertFalse(validator.validate("Abcdefghij12").valid());
    }

    @Test
    void accepts_strong_password() {
        var result = validator.validate("CorrectHorse9!");
        assertTrue(result.valid(), "Expected strong password to validate. Failures: " + result.failures());
    }

    @Test
    void reports_all_failures_at_once() {
        // "abc" — too short, no uppercase, no digit, no special. We expect at least 4 messages.
        var result = validator.validate("abc");
        assertFalse(result.valid());
        assertTrue(result.failures().size() >= 4,
                "Expected multiple failures, got: " + result.failures());
    }
}
