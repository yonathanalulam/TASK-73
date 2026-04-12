package com.dojostay.common.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA AttributeConverter that encrypts sensitive string columns at rest using
 * AES-GCM-256. The encrypted payload is stored as a base64 string with the
 * layout {@code "enc:v1:" + base64(iv || ciphertext+tag)} so that:
 *
 * <ul>
 *   <li>Rows written by older versions of this code that still contain
 *       plaintext (no {@code enc:v1:} prefix) remain readable until they are
 *       rewritten by the normal update flow. This avoids a blocking migration
 *       for existing deployments.</li>
 *   <li>Encryption is <b>fail-closed</b>: if no key is configured and the
 *       active profile is not explicitly "dev", the converter refuses to
 *       persist or read sensitive data. In the "dev" profile a missing key
 *       is tolerated with a loud warning, but writes still throw so plaintext
 *       is never silently stored.</li>
 * </ul>
 *
 * Key lookup: {@code dojostay.security.encryption-key} (32 raw bytes, base64
 * encoded). In tests a deterministic key is injected via application-test.yml.
 */
@Component
@Converter(autoApply = false)
public class SensitiveStringConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(SensitiveStringConverter.class);
    private static final String PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Static key holder. The converter is instantiated both by Spring (as a
     * bean, so we can receive the injected key) AND by Hibernate reflectively
     * when it loads {@code @Convert(converter=...)}. The reflective instance
     * does not get a Spring-managed copy, so we stash the key into a static
     * field during bean creation and read from it in convertToEntityAttribute
     * / convertToDatabaseColumn. This is the standard workaround for making a
     * Spring-aware JPA converter.
     */
    private static volatile SecretKey keyHolder;

    /** True only when the "dev" profile is active — enables read-only legacy plaintext pass-through. */
    private static volatile boolean devMode = false;

    public SensitiveStringConverter() {
        // Hibernate-built instance, no key injection
    }

    @Autowired
    public SensitiveStringConverter(
            @Value("${dojostay.security.encryption-key:}") String base64Key,
            @Value("${spring.profiles.active:}") String activeProfiles) {
        boolean isDev = activeProfiles != null && activeProfiles.contains("dev");
        devMode = isDev;

        if (base64Key == null || base64Key.isBlank()) {
            if (isDev) {
                log.warn("dojostay.security.encryption-key is not set — running in DEV mode. "
                        + "Sensitive field writes will be REJECTED. "
                        + "Set a 32-byte base64 key to enable encryption.");
                keyHolder = null;
                return;
            }
            throw new IllegalStateException(
                    "dojostay.security.encryption-key is REQUIRED outside of the dev profile. "
                    + "Set a 32-byte base64 AES-256 key via environment variable "
                    + "DOJOSTAY_SECURITY_ENCRYPTION_KEY or in the profile-specific YAML.");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(base64Key);
            if (raw.length != 32) {
                throw new IllegalStateException("encryption key must decode to exactly 32 bytes (256 bits), got "
                        + raw.length);
            }
            keyHolder = new SecretKeySpec(raw, "AES");
            log.info("SensitiveStringConverter initialized with AES-GCM-256 key");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("dojostay.security.encryption-key must be base64-encoded", e);
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        SecretKey key = keyHolder;
        if (key == null) {
            // Fail closed: never write plaintext regardless of profile.
            throw new IllegalStateException(
                    "Cannot persist sensitive field: encryption key is not configured. "
                    + "Set dojostay.security.encryption-key before writing sensitive data.");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = c.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (IllegalStateException e) {
            throw e; // re-throw our own fail-closed exception
        } catch (Exception e) {
            throw new IllegalStateException("sensitive field encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        if (!dbData.startsWith(PREFIX)) {
            // Legacy plaintext row — only allow pass-through in dev mode for
            // migration convenience. In all other profiles, reject.
            if (devMode) {
                log.warn("Reading legacy plaintext sensitive field — will require encryption on next write");
                return dbData;
            }
            throw new IllegalStateException(
                    "Encountered unencrypted sensitive data in a non-dev environment. "
                    + "Run a data migration to encrypt existing plaintext rows.");
        }
        SecretKey key = keyHolder;
        if (key == null) {
            // Fail closed: cannot decrypt without a key.
            throw new IllegalStateException(
                    "Cannot decrypt sensitive field: encryption key is not configured. "
                    + "Set dojostay.security.encryption-key to read encrypted data.");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(dbData.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            byte[] ciphertext = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, IV_BYTES, ciphertext, 0, ciphertext.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("sensitive field decryption failed", e);
        }
    }

    /** Visible for testing only — resets the static key holder. */
    public static void resetForTest() {
        keyHolder = null;
        devMode = false;
    }
}
