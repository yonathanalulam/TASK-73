package com.dojostay.scopes;

import com.dojostay.common.ApiResponse;
import com.dojostay.scopes.dto.DataScopeRuleResponse;
import com.dojostay.scopes.dto.ReplaceScopeRulesRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin-manageable scope rule API. Allows listing and replacing scope rules
 * for any user. All endpoints gated on scopes.read / scopes.write permissions.
 */
@RestController
@RequestMapping("/api/scopes")
public class DataScopeController {

    private final DataScopeService dataScopeService;
    private final DataScopeRuleRepository ruleRepository;

    public DataScopeController(DataScopeService dataScopeService,
                               DataScopeRuleRepository ruleRepository) {
        this.dataScopeService = dataScopeService;
        this.ruleRepository = ruleRepository;
    }

    /** List all scope rules (admin view). */
    @GetMapping
    @PreAuthorize("hasAuthority('scopes.read')")
    public ApiResponse<List<DataScopeRuleResponse>> listAll() {
        return ApiResponse.ok(ruleRepository.findAll().stream()
                .map(DataScopeController::toResponse)
                .toList());
    }

    /** List scope rules for a specific user. */
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('scopes.read')")
    public ApiResponse<List<DataScopeRuleResponse>> listForUser(@PathVariable Long userId) {
        return ApiResponse.ok(ruleRepository.findByUserId(userId).stream()
                .map(DataScopeController::toResponse)
                .toList());
    }

    /** Replace the full set of scope rules for a user. */
    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('scopes.write')")
    public ApiResponse<List<DataScopeRuleResponse>> replaceForUser(
            @PathVariable Long userId,
            @Valid @RequestBody ReplaceScopeRulesRequest req,
            HttpServletRequest httpReq) {
        List<DataScopeRule> newRules = new ArrayList<>();
        for (ReplaceScopeRulesRequest.RuleEntry entry : req.rules()) {
            DataScopeRule rule = new DataScopeRule();
            rule.setScopeType(entry.scopeType());
            rule.setScopeTargetId(entry.scopeTargetId());
            newRules.add(rule);
        }
        dataScopeService.replaceRules(userId, newRules, clientIp(httpReq));
        return ApiResponse.ok(ruleRepository.findByUserId(userId).stream()
                .map(DataScopeController::toResponse)
                .toList());
    }

    private static DataScopeRuleResponse toResponse(DataScopeRule r) {
        return new DataScopeRuleResponse(
                r.getId(), r.getUserId(), r.getScopeType(),
                r.getScopeTargetId(), r.getCreatedAt());
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
