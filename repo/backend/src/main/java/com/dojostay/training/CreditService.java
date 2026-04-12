package com.dojostay.training;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.common.exception.NotFoundException;
import com.dojostay.scopes.DataScopeService;
import com.dojostay.scopes.EffectiveScope;
import com.dojostay.students.Student;
import com.dojostay.students.StudentRepository;
import com.dojostay.training.dto.CreditAdjustmentRequest;
import com.dojostay.training.dto.CreditBalanceResponse;
import com.dojostay.training.dto.CreditTransactionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Append-only student credit ledger. The public surface is:
 *
 * <ul>
 *   <li>{@link #getBalance(Long)} — reads the most recent {@code balanceAfter}.</li>
 *   <li>{@link #adjust(CreditAdjustmentRequest, String)} — posts a signed delta
 *       and refuses to take the balance below zero.</li>
 *   <li>{@link #history(Long)} — returns rows most-recent-first.</li>
 * </ul>
 *
 * Consumers never update or delete a row — a refund or reversal is a new row
 * with the opposite {@code delta}. This makes the ledger fully auditable.
 */
@Service
public class CreditService {

    private final CreditTransactionRepository ledgerRepository;
    private final StudentRepository studentRepository;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;

    public CreditService(CreditTransactionRepository ledgerRepository,
                         StudentRepository studentRepository,
                         DataScopeService dataScopeService,
                         AuditService auditService) {
        this.ledgerRepository = ledgerRepository;
        this.studentRepository = studentRepository;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public CreditBalanceResponse getBalance(Long studentId) {
        Student s = loadAccessibleStudent(studentId);
        int balance = ledgerRepository.findFirstByStudentIdOrderByIdDesc(s.getId())
                .map(CreditTransaction::getBalanceAfter)
                .orElse(0);
        return new CreditBalanceResponse(s.getId(), balance);
    }

    @Transactional(readOnly = true)
    public List<CreditTransactionResponse> history(Long studentId) {
        Student s = loadAccessibleStudent(studentId);
        return ledgerRepository.findByStudentIdOrderByIdDesc(s.getId()).stream()
                .map(CreditService::toResponse)
                .toList();
    }

    @Transactional
    public CreditTransactionResponse adjust(CreditAdjustmentRequest req, String sourceIp) {
        if (req.delta() == null || req.delta() == 0) {
            throw new BusinessException("ZERO_DELTA",
                    "Credit delta must be non-zero", HttpStatus.BAD_REQUEST);
        }
        Student student = loadAccessibleStudent(req.studentId());

        int currentBalance = ledgerRepository.findFirstByStudentIdOrderByIdDesc(student.getId())
                .map(CreditTransaction::getBalanceAfter)
                .orElse(0);
        int newBalance = currentBalance + req.delta();
        if (newBalance < 0) {
            throw new BusinessException("INSUFFICIENT_CREDIT",
                    "Credit balance cannot go below zero (current=" + currentBalance
                            + ", delta=" + req.delta() + ")",
                    HttpStatus.CONFLICT);
        }

        CreditTransaction tx = new CreditTransaction();
        tx.setStudentId(student.getId());
        tx.setOrganizationId(student.getOrganizationId());
        tx.setDelta(req.delta());
        tx.setBalanceAfter(newBalance);
        tx.setReason(req.reason());
        tx.setReferenceType(req.referenceType());
        tx.setReferenceId(req.referenceId());
        tx.setNotes(req.notes());
        tx.setCreatedByUserId(actorId() != null ? actorId() : 0L);
        CreditTransaction saved = ledgerRepository.save(tx);

        AuditAction action = req.delta() > 0
                ? AuditAction.CREDIT_GRANTED
                : AuditAction.CREDIT_CONSUMED;
        auditService.record(action, actorId(), actorUsername(),
                "STUDENT", String.valueOf(student.getId()),
                "Credit delta " + req.delta() + " reason=" + req.reason()
                        + " balance_after=" + newBalance,
                sourceIp);
        return toResponse(saved);
    }

    private Student loadAccessibleStudent(Long studentId) {
        Student s = studentRepository.findById(studentId)
                .orElseThrow(() -> new NotFoundException("Student not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!scope.fullAccess() && !scope.hasOrganization(s.getOrganizationId())) {
            throw new NotFoundException("Student not found");
        }
        return s;
    }

    private static CreditTransactionResponse toResponse(CreditTransaction t) {
        return new CreditTransactionResponse(
                t.getId(), t.getStudentId(), t.getOrganizationId(),
                t.getDelta(), t.getBalanceAfter(), t.getReason(),
                t.getReferenceType(), t.getReferenceId(), t.getNotes(),
                t.getCreatedAt()
        );
    }

    private static Long actorId() {
        return CurrentUserResolver.current().map(CurrentUser::id).orElse(null);
    }

    private static String actorUsername() {
        return CurrentUserResolver.current().map(CurrentUser::username).orElse("system");
    }
}
