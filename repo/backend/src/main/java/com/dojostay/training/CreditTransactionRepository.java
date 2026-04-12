package com.dojostay.training;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CreditTransactionRepository
        extends JpaRepository<CreditTransaction, Long>, JpaSpecificationExecutor<CreditTransaction> {

    /** Ledger rows ordered most-recent-first, for statements or audit views. */
    List<CreditTransaction> findByStudentIdOrderByIdDesc(Long studentId);

    /**
     * Most-recent ledger row for a student. The current balance is the
     * {@code balanceAfter} on this row, or 0 when absent — we never re-sum the
     * ledger on reads.
     */
    Optional<CreditTransaction> findFirstByStudentIdOrderByIdDesc(Long studentId);
}
