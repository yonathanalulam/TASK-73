package com.dojostay.bootstrap;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.organizations.Department;
import com.dojostay.organizations.DepartmentRepository;
import com.dojostay.organizations.FacilityArea;
import com.dojostay.organizations.FacilityAreaRepository;
import com.dojostay.organizations.FacilityScopeType;
import com.dojostay.organizations.Organization;
import com.dojostay.organizations.OrganizationRepository;
import com.dojostay.roles.Permission;
import com.dojostay.roles.PermissionRepository;
import com.dojostay.roles.Resource;
import com.dojostay.roles.ResourceRepository;
import com.dojostay.roles.Role;
import com.dojostay.roles.RoleRepository;
import com.dojostay.roles.UserRoleType;
import com.dojostay.ops.FeatureToggle;
import com.dojostay.ops.FeatureToggleRepository;
import com.dojostay.scopes.DataScopeRule;
import com.dojostay.scopes.DataScopeRuleRepository;
import com.dojostay.users.User;
import com.dojostay.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Idempotent bootstrap data: base resources, permissions, roles, org structure, and
 * default users. Phase 2 adds the organization/department/facility area sample data
 * and a scoped staff user so the data-scope filter can be exercised locally.
 *
 * <p>Runs on application start when {@code dojostay.bootstrap.seed-on-start=true}.
 * Each entity is created only if missing, so this is safe to keep enabled across
 * restarts. The default admin and sample staff passwords are read from configuration;
 * if a password is missing the corresponding user is simply not created rather than
 * falling back to an embedded default.
 */
@Configuration
@EnableConfigurationProperties(BootstrapProperties.class)
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /**
     * Resource buckets used to group permissions in admin UIs. Adding a new
     * resource here is all that is required — no migration needed because the
     * link from permissions to resources is a soft reference on the code.
     */
    private static final Map<String, String> SEED_RESOURCES = Map.ofEntries(
            Map.entry("auth", "Authentication"),
            Map.entry("users", "Users"),
            Map.entry("audit", "Audit Log"),
            Map.entry("admin", "Admin Console"),
            Map.entry("orgs", "Organizations"),
            Map.entry("students", "Students"),
            Map.entry("property", "Property & Lodging"),
            Map.entry("reservations", "Reservations"),
            Map.entry("training", "Training"),
            Map.entry("bookings", "Bookings"),
            Map.entry("credits", "Credits"),
            Map.entry("community", "Community"),
            Map.entry("notifications", "Notifications"),
            Map.entry("credentials", "Credentials"),
            Map.entry("risk", "Risk & Incidents"),
            Map.entry("ops", "Operations")
    );

    /**
     * Permission catalog: code → (displayName, resourceCode). Future phases will
     * add more codes here (students, bookings, exports, etc.) and the seeder will
     * reconcile them automatically.
     */
    private static final List<PermissionSeed> SEED_PERMISSIONS = List.of(
            // Phase 1/2
            new PermissionSeed("auth.unlock", "Unlock locked accounts", "auth"),
            new PermissionSeed("users.read", "View users", "users"),
            new PermissionSeed("users.write", "Create or modify users", "users"),
            new PermissionSeed("audit.read", "View audit logs", "audit"),
            new PermissionSeed("admin.console", "Access admin console", "admin"),
            // Phase 3
            new PermissionSeed("orgs.read", "View organizations", "orgs"),
            new PermissionSeed("orgs.write", "Create or modify organizations", "orgs"),
            new PermissionSeed("students.read", "View students", "students"),
            new PermissionSeed("students.write", "Create or modify students", "students"),
            new PermissionSeed("students.import", "Bulk import students from CSV", "students"),
            // Phase 4
            new PermissionSeed("property.read", "View property", "property"),
            new PermissionSeed("property.write", "Create or modify property", "property"),
            new PermissionSeed("reservations.read", "View reservations", "reservations"),
            new PermissionSeed("reservations.write", "Create or cancel reservations", "reservations"),
            // Phase 5
            new PermissionSeed("training.read", "View training classes and sessions", "training"),
            new PermissionSeed("training.write", "Create or modify training offerings", "training"),
            new PermissionSeed("bookings.read", "View bookings", "bookings"),
            new PermissionSeed("bookings.write", "Create or cancel bookings", "bookings"),
            new PermissionSeed("credits.read", "View student credit balances", "credits"),
            new PermissionSeed("credits.write", "Grant or consume student credits", "credits"),
            // Phase 6
            new PermissionSeed("community.read", "View community content", "community"),
            new PermissionSeed("community.write", "Post or comment in the community", "community"),
            new PermissionSeed("moderation.review", "Review moderation reports", "community"),
            new PermissionSeed("notifications.read", "View own notifications", "notifications"),
            // Phase 7
            new PermissionSeed("credentials.review", "Review credential submissions", "credentials"),
            new PermissionSeed("risk.read", "View risk flags and incident log", "risk"),
            new PermissionSeed("risk.write", "Raise or clear risk flags", "risk"),
            // Phase 8
            new PermissionSeed("ops.toggles.read", "View feature toggles", "ops"),
            new PermissionSeed("ops.toggles.write", "Change feature toggles", "ops"),
            new PermissionSeed("ops.backups.read", "View backup status", "ops"),
            new PermissionSeed("exports.read", "Request data exports", "ops"),
            // Student self-service
            new PermissionSeed("students.self.read", "View own student profile", "students"),
            new PermissionSeed("students.self.write", "Edit own student profile", "students"),
            // Scope management
            new PermissionSeed("scopes.read", "View data scope rules", "admin"),
            new PermissionSeed("scopes.write", "Manage data scope rules", "admin")
    );

    /**
     * Initial role definitions: code → (display name, permission codes).
     */
    /**
     * Every role gets a curated permission set. The seeder never removes permissions
     * from existing roles on restart, so admins can grant extra permissions through
     * the UI (future phase) without having them stripped on the next boot.
     */
    private static final List<RoleSeed> SEED_ROLES = List.of(
            new RoleSeed("ADMIN", "Administrator", Set.of(
                    "auth.unlock", "users.read", "users.write", "audit.read", "admin.console",
                    "orgs.read", "orgs.write",
                    "students.read", "students.write", "students.import",
                    "property.read", "property.write",
                    "reservations.read", "reservations.write",
                    "training.read", "training.write",
                    "bookings.read", "bookings.write",
                    "credits.read", "credits.write",
                    "community.read", "community.write", "moderation.review",
                    "notifications.read",
                    "credentials.review", "risk.read", "risk.write",
                    "ops.toggles.read", "ops.toggles.write",
                    "ops.backups.read", "exports.read",
                    "scopes.read", "scopes.write"
            )),
            new RoleSeed("STAFF", "Organization Staff", Set.of(
                    "users.read", "audit.read",
                    "orgs.read",
                    "students.read", "students.write", "students.import",
                    "property.read", "reservations.read", "reservations.write",
                    "training.read", "training.write",
                    "bookings.read", "bookings.write",
                    "credits.read", "credits.write",
                    "community.read", "community.write", "moderation.review",
                    "notifications.read",
                    "credentials.review", "risk.read", "risk.write",
                    "ops.backups.read", "exports.read"
            )),
            new RoleSeed("STUDENT", "Student / Customer", Set.of(
                    "students.self.read", "students.self.write",
                    "bookings.read", "bookings.write",
                    "training.read",
                    "property.read",
                    "community.read", "community.write",
                    "notifications.read"
            )),
            new RoleSeed("PHOTOGRAPHER", "Photographer", Set.of(
                    "training.read",
                    "community.read", "community.write",
                    "notifications.read"
            ))
    );

    private static final String SAMPLE_ORG_CODE = "HQ";
    private static final String SAMPLE_DEPT_CODE = "MAIN";
    private static final String SAMPLE_FACILITY_CODE = "DOJO-1";

    private final BootstrapProperties props;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final ResourceRepository resourceRepository;
    private final OrganizationRepository organizationRepository;
    private final DepartmentRepository departmentRepository;
    private final FacilityAreaRepository facilityAreaRepository;
    private final DataScopeRuleRepository dataScopeRuleRepository;
    private final FeatureToggleRepository featureToggleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public DataSeeder(BootstrapProperties props,
                      UserRepository userRepository,
                      RoleRepository roleRepository,
                      PermissionRepository permissionRepository,
                      ResourceRepository resourceRepository,
                      OrganizationRepository organizationRepository,
                      DepartmentRepository departmentRepository,
                      FacilityAreaRepository facilityAreaRepository,
                      DataScopeRuleRepository dataScopeRuleRepository,
                      FeatureToggleRepository featureToggleRepository,
                      PasswordEncoder passwordEncoder,
                      AuditService auditService) {
        this.props = props;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.resourceRepository = resourceRepository;
        this.organizationRepository = organizationRepository;
        this.departmentRepository = departmentRepository;
        this.facilityAreaRepository = facilityAreaRepository;
        this.dataScopeRuleRepository = dataScopeRuleRepository;
        this.featureToggleRepository = featureToggleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (!props.isSeedOnStart()) {
            log.info("[seed] dojostay.bootstrap.seed-on-start=false — skipping");
            return;
        }
        seedResources();
        seedPermissions();
        seedRoles();
        SampleOrg sample = seedSampleOrg();
        seedAdmin();
        seedSampleStaff(sample);
        seedFeatureToggles();
    }

    private void seedResources() {
        SEED_RESOURCES.forEach((code, label) -> resourceRepository.findByCode(code).orElseGet(() -> {
            Resource r = new Resource();
            r.setCode(code);
            r.setDisplayName(label);
            Resource saved = resourceRepository.save(r);
            log.info("[seed] resource created: {}", code);
            return saved;
        }));
    }

    private void seedPermissions() {
        for (PermissionSeed seed : SEED_PERMISSIONS) {
            Permission p = permissionRepository.findByCode(seed.code()).orElseGet(() -> {
                Permission fresh = new Permission();
                fresh.setCode(seed.code());
                fresh.setDisplayName(seed.displayName());
                fresh.setResourceCode(seed.resourceCode());
                Permission saved = permissionRepository.save(fresh);
                log.info("[seed] permission created: {}", seed.code());
                return saved;
            });
            // Backfill resource_code for permissions that existed before Phase 2.
            if (p.getResourceCode() == null && seed.resourceCode() != null) {
                p.setResourceCode(seed.resourceCode());
                permissionRepository.save(p);
            }
        }
    }

    private void seedRoles() {
        for (RoleSeed seed : SEED_ROLES) {
            Role role = roleRepository.findByCode(seed.code()).orElseGet(() -> {
                Role r = new Role();
                r.setCode(seed.code());
                r.setDisplayName(seed.displayName());
                return roleRepository.save(r);
            });
            // Reconcile permissions: only add missing ones, never remove (so manual
            // admin changes via the UI in later phases are preserved across restarts).
            Set<Permission> current = role.getPermissions();
            for (String permCode : seed.permissionCodes()) {
                boolean alreadyPresent = current.stream().anyMatch(p -> p.getCode().equals(permCode));
                if (!alreadyPresent) {
                    permissionRepository.findByCode(permCode).ifPresent(current::add);
                }
            }
            roleRepository.save(role);
        }
    }

    private SampleOrg seedSampleOrg() {
        Organization org = organizationRepository.findByCode(SAMPLE_ORG_CODE).orElseGet(() -> {
            Organization o = new Organization();
            o.setCode(SAMPLE_ORG_CODE);
            o.setName("DojoStay HQ");
            o.setActive(true);
            Organization saved = organizationRepository.save(o);
            log.info("[seed] organization created: {}", SAMPLE_ORG_CODE);
            return saved;
        });

        Department dept = departmentRepository.findByOrganizationId(org.getId()).stream()
                .filter(d -> SAMPLE_DEPT_CODE.equals(d.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    Department d = new Department();
                    d.setOrganizationId(org.getId());
                    d.setCode(SAMPLE_DEPT_CODE);
                    d.setName("Main Hall");
                    d.setActive(true);
                    Department saved = departmentRepository.save(d);
                    log.info("[seed] department created: {}/{}", SAMPLE_ORG_CODE, SAMPLE_DEPT_CODE);
                    return saved;
                });

        FacilityArea facility = facilityAreaRepository.findByOrganizationId(org.getId()).stream()
                .filter(f -> SAMPLE_FACILITY_CODE.equals(f.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    FacilityArea f = new FacilityArea();
                    f.setOrganizationId(org.getId());
                    f.setDepartmentId(dept.getId());
                    f.setCode(SAMPLE_FACILITY_CODE);
                    f.setName("Primary Dojo");
                    f.setActive(true);
                    FacilityArea saved = facilityAreaRepository.save(f);
                    log.info("[seed] facility area created: {}/{}/{}",
                            SAMPLE_ORG_CODE, SAMPLE_DEPT_CODE, SAMPLE_FACILITY_CODE);
                    return saved;
                });

        return new SampleOrg(org, dept, facility);
    }

    private void seedAdmin() {
        if (props.getAdminPassword() == null || props.getAdminPassword().isBlank()) {
            log.warn("[seed] dojostay.bootstrap.admin-password is not set — admin user NOT created");
            return;
        }
        if (userRepository.existsByUsername(props.getAdminUsername())) {
            return;
        }
        Role adminRole = roleRepository.findByCode("ADMIN").orElseThrow();
        User admin = new User();
        admin.setUsername(props.getAdminUsername());
        admin.setFullName("Default Administrator");
        admin.setPasswordHash(passwordEncoder.encode(props.getAdminPassword()));
        admin.setPrimaryRole(UserRoleType.ADMIN);
        admin.setEnabled(true);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepository.save(admin);
        log.info("[seed] default admin user created: {}", props.getAdminUsername());

        auditService.record(
                AuditAction.USER_CREATED,
                null,
                "system",
                "USER",
                String.valueOf(admin.getId()),
                "Default admin created by bootstrap seeder",
                null
        );
    }

    /**
     * Creates a dev-only staff user scoped to the sample organization so Phase 2
     * data-scope filtering can be exercised locally. Only runs when the sample
     * staff password is configured (normally only in {@code application-dev.yml}).
     */
    private void seedSampleStaff(SampleOrg sample) {
        if (props.getSampleStaffPassword() == null || props.getSampleStaffPassword().isBlank()) {
            return;
        }
        if (userRepository.existsByUsername(props.getSampleStaffUsername())) {
            return;
        }
        Role staffRole = roleRepository.findByCode("STAFF").orElseThrow();
        User staff = new User();
        staff.setUsername(props.getSampleStaffUsername());
        staff.setFullName("Sample HQ Staff");
        staff.setPasswordHash(passwordEncoder.encode(props.getSampleStaffPassword()));
        staff.setPrimaryRole(UserRoleType.STAFF);
        staff.setOrganizationId(sample.organization().getId());
        staff.setEnabled(true);
        staff.setRoles(new HashSet<>(Set.of(staffRole)));
        User saved = userRepository.save(staff);

        DataScopeRule rule = new DataScopeRule();
        rule.setUserId(saved.getId());
        rule.setScopeType(FacilityScopeType.ORGANIZATION);
        rule.setScopeTargetId(sample.organization().getId());
        dataScopeRuleRepository.save(rule);

        log.info("[seed] sample staff user created: {} scoped to org {}",
                props.getSampleStaffUsername(), SAMPLE_ORG_CODE);

        auditService.record(
                AuditAction.USER_CREATED,
                null,
                "system",
                "USER",
                String.valueOf(saved.getId()),
                "Sample staff user created by bootstrap seeder",
                null
        );
        auditService.record(
                AuditAction.DATA_SCOPE_CHANGED,
                null,
                "system",
                "USER",
                String.valueOf(saved.getId()),
                "Sample staff scoped to organization " + SAMPLE_ORG_CODE,
                null
        );
    }

    /**
     * Seeds default feature toggles so the degradation system is operable
     * from the first boot. Community is enabled by default; read-only modes
     * are off by default.
     */
    private void seedFeatureToggles() {
        seedToggle("community.enabled", "Enable community posting/commenting", true);
        seedToggle("bookings.read-only", "When active, booking mutations are blocked", false);
        seedToggle("property.read-only", "When active, property mutations are blocked", false);
    }

    private void seedToggle(String code, String description, boolean enabled) {
        featureToggleRepository.findByCode(code).orElseGet(() -> {
            FeatureToggle t = new FeatureToggle();
            t.setCode(code);
            t.setDescription(description);
            t.setEnabled(enabled);
            FeatureToggle saved = featureToggleRepository.save(t);
            log.info("[seed] feature toggle created: {} enabled={}", code, enabled);
            return saved;
        });
    }

    private record PermissionSeed(String code, String displayName, String resourceCode) {
    }

    private record RoleSeed(String code, String displayName, Set<String> permissionCodes) {
    }

    private record SampleOrg(Organization organization, Department department, FacilityArea facility) {
    }
}
