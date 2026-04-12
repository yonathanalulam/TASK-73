package com.dojostay.users;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.auth.PasswordPolicyValidator;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.common.exception.NotFoundException;
import com.dojostay.roles.Role;
import com.dojostay.roles.RoleRepository;
import com.dojostay.scopes.DataScopeService;
import com.dojostay.scopes.DataScopeSpec;
import com.dojostay.scopes.EffectiveScope;
import com.dojostay.users.dto.AssignRolesRequest;
import com.dojostay.users.dto.CreateUserRequest;
import com.dojostay.users.dto.UpdateUserRequest;
import com.dojostay.users.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin-facing user management service.
 *
 * <p>All list queries go through {@link DataScopeSpec#byOrganization} so non-admin
 * staff can only see users belonging to organizations they have been granted a scope
 * rule for. This is the Phase 2 proof point: the scoped query filter is enforced at
 * the repository layer, not the controller layer, so it cannot be bypassed by a
 * forgotten check.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       PasswordPolicyValidator passwordPolicyValidator,
                       DataScopeService dataScopeService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        Specification<User> scopeSpec = DataScopeSpec.byFullScope(
                "organizationId", "departmentId", "facilityAreaId", scope);
        return userRepository.findAll(scopeSpec, pageable).map(UserService::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        User user = loadAccessible(id);
        return toResponse(user);
    }

    @Transactional
    public UserResponse create(CreateUserRequest req, String sourceIp) {
        if (userRepository.existsByUsername(req.username())) {
            throw new BusinessException("USERNAME_TAKEN",
                    "A user with that username already exists",
                    HttpStatus.CONFLICT);
        }
        var policy = passwordPolicyValidator.validate(req.password());
        if (!policy.valid()) {
            throw new BusinessException("WEAK_PASSWORD",
                    String.join("; ", policy.failures()),
                    HttpStatus.BAD_REQUEST);
        }

        User user = new User();
        user.setUsername(req.username());
        user.setFullName(req.fullName());
        user.setEmail(req.email());
        user.setPrimaryRole(req.primaryRole());
        user.setOrganizationId(req.organizationId());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setEnabled(true);
        user.setRoles(resolveRoles(req.roleCodes()));
        User saved = userRepository.save(user);

        auditRoleChange(saved, req.roleCodes(), "Created user", AuditAction.USER_CREATED, sourceIp);
        return toResponse(saved);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req, String sourceIp) {
        User user = loadAccessible(id);
        if (req.fullName() != null) user.setFullName(req.fullName());
        if (req.email() != null) user.setEmail(req.email());
        if (req.organizationId() != null) user.setOrganizationId(req.organizationId());
        if (req.enabled() != null && req.enabled() != user.isEnabled()) {
            user.setEnabled(req.enabled());
            auditService.record(
                    req.enabled() ? AuditAction.USER_ENABLED : AuditAction.USER_DISABLED,
                    actorId(), actorUsername(),
                    "USER", String.valueOf(user.getId()),
                    req.enabled() ? "User enabled" : "User disabled",
                    sourceIp
            );
        }
        User saved = userRepository.save(user);
        auditService.record(AuditAction.USER_UPDATED, actorId(), actorUsername(),
                "USER", String.valueOf(saved.getId()), "User profile updated", sourceIp);
        return toResponse(saved);
    }

    @Transactional
    public UserResponse assignRoles(Long id, AssignRolesRequest req, String sourceIp) {
        User user = loadAccessible(id);
        Set<String> before = user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
        user.setRoles(resolveRoles(req.roleCodes()));
        User saved = userRepository.save(user);

        Set<String> added = new HashSet<>(req.roleCodes());
        added.removeAll(before);
        Set<String> removed = new HashSet<>(before);
        removed.removeAll(req.roleCodes());

        for (String code : added) {
            auditService.record(AuditAction.ROLE_ASSIGNED, actorId(), actorUsername(),
                    "USER", String.valueOf(saved.getId()),
                    "Role assigned: " + code, sourceIp);
        }
        for (String code : removed) {
            auditService.record(AuditAction.ROLE_REVOKED, actorId(), actorUsername(),
                    "USER", String.valueOf(saved.getId()),
                    "Role revoked: " + code, sourceIp);
        }
        return toResponse(saved);
    }

    /**
     * Loads a user by id, but first verifies the current user's data scope allows
     * visibility. Non-admins cannot read or write users outside their scope.
     */
    private User loadAccessible(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (scope.fullAccess()) {
            return user;
        }
        if (user.getOrganizationId() == null || !scope.hasOrganization(user.getOrganizationId())) {
            // Surface as 404 rather than 403 to avoid leaking existence of out-of-scope users.
            throw new NotFoundException("User not found");
        }
        return user;
    }

    private Set<Role> resolveRoles(Set<String> codes) {
        if (codes == null || codes.isEmpty()) return new HashSet<>();
        Set<Role> out = new HashSet<>();
        for (String code : codes) {
            Role r = roleRepository.findByCode(code)
                    .orElseThrow(() -> new BusinessException("UNKNOWN_ROLE",
                            "Unknown role code: " + code, HttpStatus.BAD_REQUEST));
            out.add(r);
        }
        return out;
    }

    private void auditRoleChange(User user, Set<String> addedCodes,
                                 String summary, AuditAction action, String sourceIp) {
        auditService.record(action, actorId(), actorUsername(), "USER",
                String.valueOf(user.getId()), summary, sourceIp);
        if (addedCodes != null) {
            for (String code : addedCodes) {
                auditService.record(AuditAction.ROLE_ASSIGNED, actorId(), actorUsername(),
                        "USER", String.valueOf(user.getId()),
                        "Role assigned: " + code, sourceIp);
            }
        }
    }

    private static Long actorId() {
        return CurrentUserResolver.current().map(CurrentUser::id).orElse(null);
    }

    private static String actorUsername() {
        return CurrentUserResolver.current().map(CurrentUser::username).orElse("system");
    }

    private static UserResponse toResponse(User u) {
        return new UserResponse(
                u.getId(),
                u.getUsername(),
                u.getFullName(),
                u.getEmail(),
                u.getPrimaryRole(),
                u.isEnabled(),
                u.getOrganizationId(),
                u.getRoles().stream().map(Role::getCode).collect(Collectors.toSet()),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }
}
