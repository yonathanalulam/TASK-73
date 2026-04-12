package com.dojostay.organizations;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.common.ApiResponse;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.common.exception.NotFoundException;
import com.dojostay.organizations.dto.CreateOrganizationRequest;
import com.dojostay.organizations.dto.OrganizationResponse;
import com.dojostay.organizations.dto.UpdateOrganizationRequest;
import com.dojostay.scopes.DataScopeService;
import com.dojostay.scopes.EffectiveScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administrative CRUD for organizations. Deliberately a controller-with-service so the
 * file count stays small — the mutation surface is narrow enough to not warrant a
 * dedicated service class yet.
 */
@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationRepository organizationRepository;
    private final AuditService auditService;
    private final DataScopeService dataScopeService;

    public OrganizationController(OrganizationRepository organizationRepository,
                                  AuditService auditService,
                                  DataScopeService dataScopeService) {
        this.organizationRepository = organizationRepository;
        this.auditService = auditService;
        this.dataScopeService = dataScopeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('orgs.read')")
    public ApiResponse<List<OrganizationResponse>> list() {
        // Scope filter: admins see every org; scoped users see only the orgs
        // in their EffectiveScope.organizationIds set. This previously leaked
        // every row regardless of scope (remediation audit item #10).
        EffectiveScope scope = dataScopeService.forCurrentUser();
        List<OrganizationResponse> visible = organizationRepository.findAll().stream()
                .filter(o -> scope.fullAccess() || scope.hasOrganization(o.getId()))
                .map(OrganizationController::toResponse)
                .toList();
        return ApiResponse.ok(visible);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('orgs.read')")
    public ApiResponse<OrganizationResponse> get(@PathVariable Long id) {
        Organization o = organizationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Organization not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        // Hide existence of orgs outside the user's scope with 404 rather than
        // 403 — consistent with the existence-hiding rule elsewhere in the app.
        if (!scope.fullAccess() && !scope.hasOrganization(o.getId())) {
            throw new NotFoundException("Organization not found");
        }
        return ApiResponse.ok(toResponse(o));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('orgs.write')")
    @Transactional
    public ApiResponse<OrganizationResponse> create(
            @Valid @RequestBody CreateOrganizationRequest req,
            HttpServletRequest httpReq) {
        if (organizationRepository.findByCode(req.code()).isPresent()) {
            throw new BusinessException("ORG_CODE_TAKEN",
                    "An organization with that code already exists", HttpStatus.CONFLICT);
        }
        Organization o = new Organization();
        o.setCode(req.code());
        o.setName(req.name());
        o.setParentId(req.parentId());
        o.setContactEmail(req.contactEmail());
        o.setContactPhone(req.contactPhone());
        o.setDescription(req.description());
        o.setActive(true);
        Organization saved = organizationRepository.save(o);

        auditService.record(AuditAction.ORGANIZATION_CREATED,
                actorId(), actorUsername(), "ORGANIZATION",
                String.valueOf(saved.getId()),
                "Organization created: " + saved.getCode(), clientIp(httpReq));
        return ApiResponse.ok(toResponse(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('orgs.write')")
    @Transactional
    public ApiResponse<OrganizationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrganizationRequest req,
            HttpServletRequest httpReq) {
        Organization o = organizationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Organization not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!scope.fullAccess() && !scope.hasOrganization(o.getId())) {
            throw new NotFoundException("Organization not found");
        }
        if (req.name() != null) o.setName(req.name());
        if (req.parentId() != null) o.setParentId(req.parentId());
        if (req.active() != null) o.setActive(req.active());
        if (req.contactEmail() != null) o.setContactEmail(req.contactEmail());
        if (req.contactPhone() != null) o.setContactPhone(req.contactPhone());
        if (req.description() != null) o.setDescription(req.description());
        Organization saved = organizationRepository.save(o);

        auditService.record(AuditAction.ORGANIZATION_UPDATED,
                actorId(), actorUsername(), "ORGANIZATION",
                String.valueOf(saved.getId()),
                "Organization updated: " + saved.getCode(), clientIp(httpReq));
        return ApiResponse.ok(toResponse(saved));
    }

    private static OrganizationResponse toResponse(Organization o) {
        return new OrganizationResponse(
                o.getId(), o.getCode(), o.getName(), o.getParentId(), o.isActive(),
                o.getContactEmail(), o.getContactPhone(), o.getDescription(), o.getCreatedAt()
        );
    }

    private static Long actorId() {
        return CurrentUserResolver.current().map(CurrentUser::id).orElse(null);
    }

    private static String actorUsername() {
        return CurrentUserResolver.current().map(CurrentUser::username).orElse("system");
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
