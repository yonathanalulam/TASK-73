package com.dojostay.credentials;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration for the credential-review file upload pipeline. The directory
 * defaults to a subfolder of {@code java.io.tmpdir} so the server starts
 * cleanly on a new dev box without any setup, but deployments are expected
 * to override {@code dojostay.credentials.upload-dir} to a durable path.
 *
 * <p>The mime whitelist is intentionally small: the only evidence formats we
 * accept are scanned certificates (PDF) and photos of them (JPEG/PNG).
 */
@ConfigurationProperties(prefix = "dojostay.credentials")
public class CredentialUploadProperties {

    private String uploadDir = System.getProperty("java.io.tmpdir") + "/dojostay-credentials";
    private long maxFileSize = 8L * 1024L * 1024L; // 8 MB
    private List<String> allowedMimeTypes = List.of(
            "application/pdf",
            "image/jpeg",
            "image/png"
    );

    public String getUploadDir() { return uploadDir; }
    public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }

    public long getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(long maxFileSize) { this.maxFileSize = maxFileSize; }

    public List<String> getAllowedMimeTypes() { return allowedMimeTypes; }
    public void setAllowedMimeTypes(List<String> allowedMimeTypes) { this.allowedMimeTypes = allowedMimeTypes; }
}
