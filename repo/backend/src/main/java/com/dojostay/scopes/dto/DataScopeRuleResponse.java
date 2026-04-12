package com.dojostay.scopes.dto;

import com.dojostay.organizations.FacilityScopeType;

import java.time.Instant;

public record DataScopeRuleResponse(
        Long id,
        Long userId,
        FacilityScopeType scopeType,
        Long scopeTargetId,
        Instant createdAt
) {}
