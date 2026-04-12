package com.dojostay.credentials;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface CredentialReviewRepository
        extends JpaRepository<CredentialReview, Long>, JpaSpecificationExecutor<CredentialReview> {

    /**
     * Duplicate-detection hook: find any prior review that shares the same
     * file SHA-256 fingerprint. Used to reject a student/user trying to reuse
     * someone else's evidence document.
     */
    Optional<CredentialReview> findFirstByFileSha256(String fileSha256);
}
