package com.dojostay.roles;

import com.dojostay.common.ApiResponse;
import com.dojostay.roles.dto.PermissionResponse;
import com.dojostay.roles.dto.RoleResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Read-only endpoints for the role/permission catalog. Used by the admin UI to
 * populate role pickers and permission matrices. Mutation of roles and permissions
 * is deliberately out of scope for Phase 2 — the set is seeded via {@code DataSeeder}
 * and will get a dedicated admin flow in a later phase.
 */
@RestController
@RequestMapping("/api")
public class RoleController {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RoleController(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('users.read')")
    public ApiResponse<List<RoleResponse>> listRoles() {
        List<RoleResponse> roles = roleRepository.findAll().stream()
                .map(RoleController::toRoleResponse)
                .toList();
        return ApiResponse.ok(roles);
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('users.read')")
    public ApiResponse<List<PermissionResponse>> listPermissions() {
        List<PermissionResponse> perms = permissionRepository.findAll().stream()
                .map(RoleController::toPermissionResponse)
                .toList();
        return ApiResponse.ok(perms);
    }

    private static RoleResponse toRoleResponse(Role r) {
        return new RoleResponse(
                r.getId(),
                r.getCode(),
                r.getDisplayName(),
                r.getDescription(),
                r.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet())
        );
    }

    private static PermissionResponse toPermissionResponse(Permission p) {
        return new PermissionResponse(
                p.getId(),
                p.getCode(),
                p.getDisplayName(),
                p.getDescription(),
                p.getResourceCode()
        );
    }
}
