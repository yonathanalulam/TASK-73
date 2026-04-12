package com.dojostay.scopes.dto;

import com.dojostay.organizations.FacilityScopeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReplaceScopeRulesRequest(
        @NotNull List<@Valid RuleEntry> rules
) {
    public record RuleEntry(
            @NotNull FacilityScopeType scopeType,
            @NotNull Long scopeTargetId
    ) {}
}
