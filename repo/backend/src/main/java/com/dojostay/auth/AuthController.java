package com.dojostay.auth;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.dto.ChangePasswordRequest;
import com.dojostay.auth.dto.CurrentUserResponse;
import com.dojostay.auth.dto.LoginRequest;
import com.dojostay.common.ApiResponse;
import com.dojostay.common.exception.NotFoundException;
import com.dojostay.common.exception.UnauthorizedException;
import com.dojostay.users.User;
import com.dojostay.users.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final LockoutService lockoutService;
    private final AuditService auditService;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthController(AuthService authService,
                          UserRepository userRepository,
                          LockoutService lockoutService,
                          AuditService auditService) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.lockoutService = lockoutService;
        this.auditService = auditService;
    }

    /**
     * No-op endpoint that exists so the SPA can fetch a CSRF cookie before posting login.
     */
    @GetMapping("/csrf")
    public ApiResponse<Void> csrf() {
        return ApiResponse.empty();
    }

    @PostMapping("/login")
    public ApiResponse<CurrentUserResponse> login(@Valid @RequestBody LoginRequest req,
                                                  HttpServletRequest httpReq,
                                                  HttpServletResponse httpRes) {
        Authentication authentication = authService.authenticate(
                req.username(),
                req.password(),
                clientIp(httpReq),
                httpReq.getHeader("User-Agent")
        );

        // Persist the new authentication into the session via Spring's standard mechanism.
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(authentication);
        SecurityContextHolder.setContext(ctx);
        securityContextRepository.saveContext(ctx, httpReq, httpRes);

        CurrentUser cu = (CurrentUser) authentication.getPrincipal();
        return ApiResponse.ok(toResponse(cu));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpReq, HttpServletResponse httpRes) {
        CurrentUserResolver.current().ifPresent(cu ->
                auditService.record(AuditAction.AUTH_LOGOUT, cu.id(), cu.username(),
                        "USER", String.valueOf(cu.id()), "Logout", clientIp(httpReq))
        );
        HttpSession session = httpReq.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ApiResponse.empty();
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        CurrentUser cu = CurrentUserResolver.current()
                .orElseThrow(() -> new UnauthorizedException("Not authenticated"));
        return ApiResponse.ok(toResponse(cu));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                            HttpServletRequest httpReq) {
        CurrentUser cu = CurrentUserResolver.current()
                .orElseThrow(() -> new UnauthorizedException("Not authenticated"));
        User user = userRepository.findById(cu.id())
                .orElseThrow(() -> new NotFoundException("User not found"));
        authService.changePassword(user, req.currentPassword(), req.newPassword(), clientIp(httpReq));
        return ApiResponse.empty();
    }

    @PostMapping("/unlock/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> unlock(@PathVariable Long userId, HttpServletRequest httpReq) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        CurrentUser actorCu = CurrentUserResolver.current()
                .orElseThrow(() -> new UnauthorizedException("Not authenticated"));
        User actor = userRepository.findById(actorCu.id())
                .orElseThrow(() -> new NotFoundException("Actor user not found"));
        lockoutService.adminUnlock(target, actor, clientIp(httpReq));
        return ApiResponse.empty();
    }

    private static CurrentUserResponse toResponse(CurrentUser cu) {
        return new CurrentUserResponse(
                cu.id(),
                cu.username(),
                cu.fullName(),
                cu.primaryRole(),
                new HashSet<>(cu.roles()),
                new HashSet<>(cu.permissions())
        );
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return req.getRemoteAddr();
    }
}
