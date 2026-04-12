package com.dojostay.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration for the property-image upload pipeline. Mirrors
 * {@code CredentialUploadProperties} intentionally — both flows write
 * operator-supplied binaries to a local directory and must validate mime
 * type and size up front. The directory defaults under
 * {@code java.io.tmpdir} so the server starts cleanly on a fresh dev box,
 * but deployments should override {@code dojostay.property.upload-dir} to a
 * durable path (e.g. the same NAS mount that backs credential uploads).
 *
 * <p>Mime whitelist is just JPEG and PNG — gallery assets only, no PDF.
 */
@ConfigurationProperties(prefix = "dojostay.property")
public class PropertyUploadProperties {

    private String uploadDir = System.getProperty("java.io.tmpdir") + "/dojostay-property-images";
    private long maxFileSize = 8L * 1024L * 1024L; // 8 MB
    private List<String> allowedMimeTypes = List.of(
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
