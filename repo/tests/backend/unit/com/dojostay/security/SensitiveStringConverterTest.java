package com.dojostay.security;

import com.dojostay.common.security.SensitiveStringConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SensitiveStringConverter} fail-closed behavior.
 */
class SensitiveStringConverterTest {

    /** Deterministic 32-byte base64 AES-256 key for tests. */
    private static final String TEST_KEY = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=";

    @AfterEach
    void tearDown() {
        SensitiveStringConverter.resetForTest();
    }

    @Test
    void startup_fails_without_key_in_non_dev_profile() {
        assertThrows(IllegalStateException.class,
                () -> new SensitiveStringConverter("", "prod"),
                "Missing key in prod must fail startup");
    }

    @Test
    void startup_fails_without_key_when_no_profile_set() {
        assertThrows(IllegalStateException.class,
                () -> new SensitiveStringConverter("", ""),
                "Missing key with no active profile must fail startup");
    }

    @Test
    void startup_succeeds_without_key_in_dev_profile() {
        // Should not throw — dev mode tolerates missing key at startup
        new SensitiveStringConverter("", "dev");
    }

    @Test
    void startup_succeeds_with_valid_key() {
        new SensitiveStringConverter(TEST_KEY, "prod");
    }

    @Test
    void startup_fails_with_wrong_size_key() {
        // 16-byte key (base64 of 16 bytes)
        String shortKey = "MTIzNDU2Nzg5MDEyMzQ1Ng==";
        assertThrows(IllegalStateException.class,
                () -> new SensitiveStringConverter(shortKey, "prod"));
    }

    @Test
    void encrypt_then_decrypt_round_trips() {
        SensitiveStringConverter converter = new SensitiveStringConverter(TEST_KEY, "test");
        String original = "sensitive-data-123";

        String encrypted = converter.convertToDatabaseColumn(original);
        assertTrue(encrypted.startsWith("enc:v1:"), "Encrypted value must have prefix");
        assertNotEquals(original, encrypted);

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void null_passes_through_unchanged() {
        SensitiveStringConverter converter = new SensitiveStringConverter(TEST_KEY, "test");
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void write_without_key_fails_closed_even_in_dev() {
        SensitiveStringConverter converter = new SensitiveStringConverter("", "dev");
        // Hibernate-instantiated converter (no key) must also fail on write
        SensitiveStringConverter hibernateInstance = new SensitiveStringConverter();

        assertThrows(IllegalStateException.class,
                () -> hibernateInstance.convertToDatabaseColumn("secret"),
                "Write without key must throw — no plaintext fallback");
    }

    @Test
    void read_encrypted_data_without_key_fails_closed() {
        // First encrypt with a key
        SensitiveStringConverter withKey = new SensitiveStringConverter(TEST_KEY, "test");
        String encrypted = withKey.convertToDatabaseColumn("secret");

        // Now reset key and try to read
        SensitiveStringConverter.resetForTest();
        new SensitiveStringConverter("", "dev"); // dev mode, no key
        SensitiveStringConverter noKey = new SensitiveStringConverter();

        assertThrows(IllegalStateException.class,
                () -> noKey.convertToEntityAttribute(encrypted),
                "Reading encrypted data without key must fail");
    }

    @Test
    void legacy_plaintext_read_allowed_only_in_dev() {
        // In dev mode, legacy plaintext rows pass through
        new SensitiveStringConverter("", "dev");
        SensitiveStringConverter devConverter = new SensitiveStringConverter();
        assertEquals("plain-value", devConverter.convertToEntityAttribute("plain-value"));

        // Reset and try in non-dev — should fail
        SensitiveStringConverter.resetForTest();
        new SensitiveStringConverter(TEST_KEY, "prod");
        SensitiveStringConverter prodConverter = new SensitiveStringConverter();

        assertThrows(IllegalStateException.class,
                () -> prodConverter.convertToEntityAttribute("plain-value"),
                "Plaintext read in prod must fail");
    }
}
