package com.dojostay.credentials;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.common.exception.NotFoundException;
import com.dojostay.credentials.dto.CredentialReviewResponse;
import com.dojostay.credentials.dto.DecideCredentialReviewRequest;
import com.dojostay.credentials.dto.SubmitCredentialReviewRequest;
import com.dojostay.scopes.DataScopeService;
import com.dojostay.scopes.EffectiveScope;
import com.dojostay.students.Student;
import com.dojostay.students.StudentRepository;
import com.dojostay.users.User;
import com.dojostay.users.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Belt-rank credential review workflow. A submission stays {@code SUBMITTED}
 * until a reviewer transitions it to {@code APPROVED} or {@code REJECTED};
 * decided reviews are immutable, so a re-attempt creates a new row.
 *
 * <p>Phase A3: submissions can now carry an evidence file. The service stores
 * the bytes on disk under a configurable root (see
 * {@link CredentialUploadProperties}), fingerprints them with SHA-256, and
 * rejects any fingerprint already present in {@code credential_reviews}. A
 * blacklist action on the associated user account is also exposed here so the
 * risk-control flow can hard-stop a user that tried to launder someone else's
 * certificate.
 */
@Service
public class CredentialReviewService {

    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final CredentialReviewRepository reviewRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;
    private final CredentialUploadProperties uploadProperties;

    public CredentialReviewService(CredentialReviewRepository reviewRepository,
                                   StudentRepository studentRepository,
                                   UserRepository userRepository,
                                   DataScopeService dataScopeService,
                                   AuditService auditService,
                                   CredentialUploadProperties uploadProperties) {
        this.reviewRepository = reviewRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
        this.uploadProperties = uploadProperties;
    }

    @Transactional(readOnly = true)
    public List<CredentialReviewResponse> list() {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        return reviewRepository.findAll().stream()
                .filter(r -> scope.fullAccess() || scope.hasOrganization(r.getOrganizationId()))
                .map(CredentialReviewService::toResponse)
                .toList();
    }

    /**
     * Legacy text-only submission. Preserved so JSON clients keep working, but
     * the mobile app and admin web now go through
     * {@link #submitWithFile(SubmitCredentialReviewRequest, MultipartFile, String)}.
     */
    @Transactional
    public CredentialReviewResponse submit(SubmitCredentialReviewRequest req, String sourceIp) {
        Student s = loadAccessibleStudent(req.studentId());
        CredentialReview r = new CredentialReview();
        r.setOrganizationId(s.getOrganizationId());
        r.setStudentId(s.getId());
        r.setDiscipline(req.discipline());
        r.setRequestedRank(req.requestedRank());
        r.setCurrentRank(req.currentRank());
        r.setEvidence(req.evidence());
        r.setStatus(CredentialReview.Status.SUBMITTED);
        r.setSubmittedByUserId(requireActor());
        CredentialReview saved = reviewRepository.save(r);

        auditService.record(AuditAction.CREDENTIAL_REVIEW_SUBMITTED, actorId(), actorUsername(),
                "CREDENTIAL_REVIEW", String.valueOf(saved.getId()),
                "Student " + s.getId() + " requesting " + req.requestedRank() + " in " + req.discipline(),
                sourceIp);
        return toResponse(saved);
    }

    /**
     * Multipart submission with evidence file attached. The file is validated,
     * fingerprinted, stored to the configured upload directory, and linked to
     * the review row.
     *
     * <p>Validation rules enforced here:
     * <ul>
     *   <li>{@code file} must be non-empty</li>
     *   <li>size ≤ {@link CredentialUploadProperties#getMaxFileSize()}</li>
     *   <li>mime type ∈ {@link CredentialUploadProperties#getAllowedMimeTypes()}</li>
     *   <li>SHA-256 must not match any existing review (duplicate → 409 DUPLICATE_EVIDENCE,
     *   audited as {@link AuditAction#CREDENTIAL_FILE_DUPLICATE})</li>
     * </ul>
     */
    @Transactional
    public CredentialReviewResponse submitWithFile(SubmitCredentialReviewRequest req,
                                                    MultipartFile file,
                                                    String sourceIp) {
        Student s = loadAccessibleStudent(req.studentId());

        if (file == null || file.isEmpty()) {
            throw new BusinessException("EVIDENCE_REQUIRED",
                    "An evidence file is required for this submission",
                    HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > uploadProperties.getMaxFileSize()) {
            throw new BusinessException("FILE_TOO_LARGE",
                    "Evidence file exceeds the " + uploadProperties.getMaxFileSize() + " byte limit",
                    HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String mime = file.getContentType() == null
                ? "application/octet-stream"
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (!uploadProperties.getAllowedMimeTypes().contains(mime)) {
            throw new BusinessException("FILE_TYPE_REJECTED",
                    "File type " + mime + " is not accepted for credential evidence",
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("FILE_READ_FAILED",
                    "Could not read uploaded file",
                    HttpStatus.BAD_REQUEST);
        }
        String sha256 = sha256Hex(bytes);

        // Duplicate fingerprint check — existence-hidden by still going through the
        // audit trail, so a reused certificate is permanently visible in the audit
        // log even though the client just sees a generic 409.
        Optional<CredentialReview> existing = reviewRepository.findFirstByFileSha256(sha256);
        if (existing.isPresent()) {
            CredentialReview prior = existing.get();
            auditService.record(AuditAction.CREDENTIAL_FILE_DUPLICATE, actorId(), actorUsername(),
                    "CREDENTIAL_REVIEW", String.valueOf(prior.getId()),
                    "Duplicate SHA-256 submitted by student " + s.getId()
                            + " (prior review " + prior.getId() + ", student " + prior.getStudentId() + ")",
                    sourceIp);
            throw new BusinessException("DUPLICATE_EVIDENCE",
                    "This evidence file has already been submitted",
                    HttpStatus.CONFLICT);
        }

        // Store to disk under uploadDir/<org>/<studentId>-<timestamp>-<uuid>.<ext>
        String storagePath;
        try {
            Path root = Paths.get(uploadProperties.getUploadDir(), String.valueOf(s.getOrganizationId()));
            Files.createDirectories(root);
            String ext = extensionFor(mime);
            String fname = s.getId() + "-" + FILE_STAMP.format(Instant.now()) + "-"
                    + UUID.randomUUID() + ext;
            Path target = root.resolve(fname);
            Files.copy(new java.io.ByteArrayInputStream(bytes), target, StandardCopyOption.REPLACE_EXISTING);
            storagePath = target.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new BusinessException("FILE_STORE_FAILED",
                    "Could not store uploaded file",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        CredentialReview r = new CredentialReview();
        r.setOrganizationId(s.getOrganizationId());
        r.setStudentId(s.getId());
        r.setDiscipline(req.discipline());
        r.setRequestedRank(req.requestedRank());
        r.setCurrentRank(req.currentRank());
        r.setEvidence(req.evidence());
        r.setStatus(CredentialReview.Status.SUBMITTED);
        r.setSubmittedByUserId(requireActor());
        r.setFileName(sanitizeFileName(file.getOriginalFilename()));
        r.setFileMime(mime);
        r.setFileSize(file.getSize());
        r.setFileStoragePath(storagePath);
        r.setFileSha256(sha256);
        CredentialReview saved = reviewRepository.save(r);

        auditService.record(AuditAction.CREDENTIAL_REVIEW_SUBMITTED, actorId(), actorUsername(),
                "CREDENTIAL_REVIEW", String.valueOf(saved.getId()),
                "Student " + s.getId() + " requesting " + req.requestedRank()
                        + " in " + req.discipline() + " with file sha256=" + sha256,
                sourceIp);
        return toResponse(saved);
    }

    @Transactional
    public CredentialReviewResponse decide(Long reviewId, DecideCredentialReviewRequest req,
                                           String sourceIp) {
        CredentialReview r = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Credential review not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!scope.fullAccess() && !scope.hasOrganization(r.getOrganizationId())) {
            throw new NotFoundException("Credential review not found");
        }
        if (r.getStatus() != CredentialReview.Status.SUBMITTED) {
            throw new BusinessException("REVIEW_ALREADY_DECIDED",
                    "Credential review is already decided",
                    HttpStatus.CONFLICT);
        }
        if (req.decision() == CredentialReview.Status.SUBMITTED) {
            throw new BusinessException("INVALID_DECISION",
                    "Decision must be APPROVED or REJECTED",
                    HttpStatus.BAD_REQUEST);
        }
        r.setStatus(req.decision());
        r.setReviewNotes(req.reviewNotes());
        r.setReviewedByUserId(requireActor());
        r.setDecidedAt(Instant.now());
        CredentialReview saved = reviewRepository.save(r);

        AuditAction action = req.decision() == CredentialReview.Status.APPROVED
                ? AuditAction.CREDENTIAL_REVIEW_APPROVED
                : AuditAction.CREDENTIAL_REVIEW_REJECTED;
        auditService.record(action, actorId(), actorUsername(),
                "CREDENTIAL_REVIEW", String.valueOf(saved.getId()),
                "Decision: " + req.decision(), sourceIp);
        return toResponse(saved);
    }

    /**
     * Blacklist the user account linked to the student behind the given review.
     * Scoped to reviewers who can already see this review. Sets the dedicated
     * {@code blacklisted} flag (distinct from {@code enabled}) so auth can
     * return a specific error code and the user is permanently locked out
     * until a reviewer manually clears the flag.
     */
    @Transactional
    public void blacklistSubject(Long reviewId, String reason, String sourceIp) {
        CredentialReview r = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Credential review not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!scope.fullAccess() && !scope.hasOrganization(r.getOrganizationId())) {
            throw new NotFoundException("Credential review not found");
        }
        Student s = studentRepository.findById(r.getStudentId())
                .orElseThrow(() -> new NotFoundException("Student not found"));
        if (s.getUserId() == null) {
            throw new BusinessException("STUDENT_HAS_NO_ACCOUNT",
                    "Student has no linked user account to blacklist",
                    HttpStatus.CONFLICT);
        }
        User user = userRepository.findById(s.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setBlacklisted(true);
        user.setBlacklistReason(reason);
        user.setBlacklistedAt(Instant.now());
        userRepository.save(user);

        auditService.record(AuditAction.USER_BLACKLISTED, actorId(), actorUsername(),
                "USER", String.valueOf(user.getId()),
                "Blacklisted via credential review " + reviewId + ": " + reason,
                sourceIp);
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

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String extensionFor(String mime) {
        return switch (mime) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".bin";
        };
    }

    /**
     * Strip path components from the client-provided filename so a malicious
     * {@code ../../etc/passwd} cannot escape the storage root on display.
     * Storage path is derived server-side regardless.
     */
    private static String sanitizeFileName(String name) {
        if (name == null) return null;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String bare = slash >= 0 ? name.substring(slash + 1) : name;
        return bare.length() > 255 ? bare.substring(0, 255) : bare;
    }

    private static Long actorId() {
        return CurrentUserResolver.current().map(CurrentUser::id).orElse(null);
    }

    private static String actorUsername() {
        return CurrentUserResolver.current().map(CurrentUser::username).orElse("system");
    }

    private static Long requireActor() {
        Long id = actorId();
        if (id == null) {
            throw new BusinessException("NO_ACTOR",
                    "An authenticated user is required",
                    HttpStatus.UNAUTHORIZED);
        }
        return id;
    }

    private static CredentialReviewResponse toResponse(CredentialReview r) {
        return new CredentialReviewResponse(
                r.getId(), r.getOrganizationId(), r.getStudentId(),
                r.getDiscipline(), r.getRequestedRank(), r.getCurrentRank(),
                r.getEvidence(), r.getStatus(), r.getReviewNotes(),
                r.getSubmittedByUserId(), r.getReviewedByUserId(),
                r.getCreatedAt(), r.getDecidedAt(),
                r.getFileName(), r.getFileMime(), r.getFileSize(), r.getFileSha256()
        );
    }
}
