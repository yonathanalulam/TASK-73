package com.dojostay.ops;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeatureToggleRepository extends JpaRepository<FeatureToggle, Long> {
    Optional<FeatureToggle> findByCode(String code);
}
