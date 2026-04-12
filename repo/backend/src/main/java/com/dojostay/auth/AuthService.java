package com.dojostay.auth;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.common.exception.AccountBlacklistedException;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.common.exception.UnauthorizedException;
import com.dojostay.roles.Permission;
import com.dojostay.roles.Role;
import com.dojostay.users.LoginAttempt;
import com.dojostay.users.LoginAttemptRepository;
import com.dojostay.users.User;
import com.dojostay.users.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Owns the actual login/logout/password-change logic.
 *
 * <p>Built around {@link LockoutService} so that:
 * <ol>
 *   <li>locked accounts are rejected before bcrypt is even attempted</li>
 *   <li>every wrong password increments the counter</li>
 *   <li>every success resets the counter</li>
 *   <li>every event is audited</li>
 * </ol>
 *
 * <p>To stay constant-time-ish against username probing, we always run the password
 * comparison even when the user is missing.
 */
@Service
public class AuthService {

    private static final String DUMMY_HASH =
            "$2a$12$ABCDEFGHIJKLMNOPQRSTUuvwxyz0123456789abcdefghijklmnopqrst";

    private final UserRepository userRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final LockoutService lockoutService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       LoginAttemptRepository loginAttemptRepository,
                       PasswordEncoder passwordEncoder,
                       LockoutService lockoutService,
                       PasswordPolicyValidator passwordPolicyValidator,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordEncoder = passwordEncoder;
        this.lockoutService = lockoutService;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.auditService = auditService;
    }

    @Transactional
    public Authentication authenticate(String username, String rawPassword, String sourceIp, String userAgent) {
        User user = userRepository.findByUsername(username).orElse(null);

        if (user == null) {
            // Run a dummy bcrypt to keep timing roughly equivalent and avoid leaking
            // whether the username exists.
            passwordEncoder.matches(rawPassword, DUMMY_HASH);
            recordAttempt(username, false, sourceIp, userAgent, "USER_NOT_FOUND");
            auditService.record(AuditAction.AUTH_LOGIN_FAILURE, null, username, "USER", null,
                    "Login failed (user not found)", sourceIp);
            throw new UnauthorizedException("Invalid username or password");
        }

        if (!user.isEnabled()) {
            recordAttempt(username, false, sourceIp, userAgent, "USER_DISABLED");
            auditService.record(AuditAction.AUTH_LOGIN_FAILURE, user.getId(), username, "USER",
                    String.valueOf(user.getId()), "Login failed (account disabled)", sourceIp);
            throw new UnauthorizedException("Invalid username or password");
        }

        // Blacklisted accounts: reject with a dedicated error code so the client
        // can show a distinct "contact your org admin" screen and audit gets a
        // separate breadcrumb from the temporary-lockout path. We do NOT fall
        // through to the dummy bcrypt check here — blacklist is permanent and
        // username-timing leakage is not an incremental risk at that point.
        if (user.isBlacklisted()) {
            recordAttempt(username, false, sourceIp, userAgent, "USER_BLACKLISTED");
            auditService.record(AuditAction.AUTH_LOGIN_FAILURE, user.getId(), username, "USER",
                    String.valueOf(user.getId()), "Login rejected (account blacklisted)", sourceIp);
            throw new AccountBlacklistedException("This account has been blacklisted");
        }

        // Locked accounts: reject without revealing lock state in the user-facing message,
        // but emit a distinct audit row + 423 status via the dedicated exception type.
        lockoutService.assertNotLocked(user);

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            boolean nowLocked = lockoutService.recordFailure(user, sourceIp);
            recordAttempt(username, false, sourceIp, userAgent,
                    nowLocked ? "BAD_PASSWORD_LOCKED" : "BAD_PASSWORD");
            auditService.record(AuditAction.AUTH_LOGIN_FAILURE, user.getId(), username, "USER",
                    String.valueOf(user.getId()), "Login failed (bad password)", sourceIp);
            throw new UnauthorizedException("Invalid username or password");
        }

        lockoutService.recordSuccess(user);
        recordAttempt(username, true, sourceIp, userAgent, null);
        auditService.record(AuditAction.AUTH_LOGIN_SUCCESS, user.getId(), username, "USER",
                String.valueOf(user.getId()), "Login successful", sourceIp);

        return buildAuthentication(user);
    }

    @Transactional
    public void changePassword(User user, String currentPassword, String newPassword, String sourceIp) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        var policy = passwordPolicyValidator.validate(newPassword);
        if (!policy.valid()) {
            throw new BusinessException("WEAK_PASSWORD",
                    String.join("; ", policy.failures()),
                    HttpStatus.BAD_REQUEST);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        auditService.record(AuditAction.AUTH_PASSWORD_CHANGED, user.getId(), user.getUsername(),
                "USER", String.valueOf(user.getId()), "Password changed", sourceIp);
    }

    public Authentication buildAuthentication(User user) {
        Set<String> roleCodes = user.getRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());
        Set<String> permCodes = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        // Spring Security expects "ROLE_" prefixed authorities for hasRole() checks.
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        roleCodes.forEach(c -> authorities.add(new SimpleGrantedAuthority("ROLE_" + c)));
        permCodes.forEach(c -> authorities.add(new SimpleGrantedAuthority(c)));
        // The primary role type is also exposed as a top-level role authority.
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getPrimaryRole().name()));

        CurrentUser principal = new CurrentUser(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getPrimaryRole(),
                roleCodes,
                permCodes
        );
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private void recordAttempt(String username, boolean success, String ip, String agent, String reason) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUsername(username);
        attempt.setSuccessful(success);
        attempt.setSourceIp(ip);
        attempt.setUserAgent(agent);
        attempt.setAttemptedAt(Instant.now());
        attempt.setFailureReason(reason);
        loginAttemptRepository.save(attempt);
    }

    /**
     * Convenience for tests.
     */
    public List<String> validatePasswordPolicy(String pw) {
        return passwordPolicyValidator.validate(pw).failures();
    }
}
