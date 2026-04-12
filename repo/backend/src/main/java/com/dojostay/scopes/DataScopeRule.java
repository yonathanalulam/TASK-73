package com.dojostay.scopes;

import com.dojostay.organizations.FacilityScopeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row grants one user access to one (scope_type, scope_target) pair.
 *
 * <p>A user with no rules and no ADMIN role has no data access. Admins bypass data
 * scope filtering entirely (see {@link DataScopeService#hasFullAccess}).
 */
@Entity
@Table(name = "data_scope_rules")
public class DataScopeRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private FacilityScopeType scopeType;

    @Column(name = "scope_target_id", nullable = false)
    private Long scopeTargetId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public FacilityScopeType getScopeType() { return scopeType; }
    public void setScopeType(FacilityScopeType scopeType) { this.scopeType = scopeType; }

    public Long getScopeTargetId() { return scopeTargetId; }
    public void setScopeTargetId(Long scopeTargetId) { this.scopeTargetId = scopeTargetId; }

    public Instant getCreatedAt() { return createdAt; }
}
