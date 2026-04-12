package com.dojostay.scopes;

import com.dojostay.organizations.FacilityScopeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataScopeRuleRepository extends JpaRepository<DataScopeRule, Long> {

    List<DataScopeRule> findByUserId(Long userId);

    List<DataScopeRule> findByUserIdAndScopeType(Long userId, FacilityScopeType scopeType);

    void deleteByUserId(Long userId);
}
