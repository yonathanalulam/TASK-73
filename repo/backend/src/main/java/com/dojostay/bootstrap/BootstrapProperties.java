package com.dojostay.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dojostay.bootstrap")
public class BootstrapProperties {

    private boolean seedOnStart = true;
    private String adminUsername = "admin";
    private String adminPassword;

    /**
     * When set, the seeder provisions a dev-only staff user scoped to the sample
     * organization. Used for local Phase 2 verification of data-scope filtering.
     * Leave unset in production — the sample org structure will still be created,
     * but no staff user will be auto-generated.
     */
    private String sampleStaffUsername = "staff-hq";
    private String sampleStaffPassword;

    public boolean isSeedOnStart() { return seedOnStart; }
    public void setSeedOnStart(boolean seedOnStart) { this.seedOnStart = seedOnStart; }

    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }

    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }

    public String getSampleStaffUsername() { return sampleStaffUsername; }
    public void setSampleStaffUsername(String sampleStaffUsername) { this.sampleStaffUsername = sampleStaffUsername; }

    public String getSampleStaffPassword() { return sampleStaffPassword; }
    public void setSampleStaffPassword(String sampleStaffPassword) { this.sampleStaffPassword = sampleStaffPassword; }
}
