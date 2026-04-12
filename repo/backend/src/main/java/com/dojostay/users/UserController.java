package com.dojostay.users;

import com.dojostay.common.ApiResponse;
import com.dojostay.users.dto.AssignRolesRequest;
import com.dojostay.users.dto.CreateUserRequest;
import com.dojostay.users.dto.UpdateUserRequest;
import com.dojostay.users.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-facing user management endpoints.
 *
 * <p>Authorization is enforced at two layers:
 * <ul>
 *   <li>{@link PreAuthorize} gates each endpoint by permission code
 *       ({@code users.read} / {@code users.write}).</li>
 *   <li>{@link UserService} additionally filters list results by the caller's
 *       {@link com.dojostay.scopes.EffectiveScope}, and refuses to load users
 *       outside that scope (surfaced as 404, not 403, to avoid leaking existence).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('users.read')")
    public ApiResponse<Page<UserResponse>> list(Pageable pageable) {
        return ApiResponse.ok(userService.list(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('users.read')")
    public ApiResponse<UserResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(userService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('users.write')")
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest req,
                                            HttpServletRequest httpReq) {
        return ApiResponse.ok(userService.create(req, clientIp(httpReq)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('users.write')")
    public ApiResponse<UserResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody UpdateUserRequest req,
                                            HttpServletRequest httpReq) {
        return ApiResponse.ok(userService.update(id, req, clientIp(httpReq)));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('users.write')")
    public ApiResponse<UserResponse> assignRoles(@PathVariable Long id,
                                                 @Valid @RequestBody AssignRolesRequest req,
                                                 HttpServletRequest httpReq) {
        return ApiResponse.ok(userService.assignRoles(id, req, clientIp(httpReq)));
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
