package com.dojostay.students;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.common.exception.NotFoundException;
import com.dojostay.common.security.DataMasking;
import com.dojostay.scopes.DataScopeService;
import com.dojostay.scopes.DataScopeSpec;
import com.dojostay.scopes.EffectiveScope;
import com.dojostay.students.dto.BulkImportResult;
import com.dojostay.students.dto.CreateStudentRequest;
import com.dojostay.students.dto.StudentResponse;
import com.dojostay.students.dto.UpdateStudentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Student management with scope-aware reads, audited writes, and CSV bulk import.
 *
 * <p>Scope filter identical to {@code UserService}: admins bypass, everyone else is
 * restricted to the organization ids in their {@code EffectiveScope}. Out-of-scope
 * reads surface as 404 for the same existence-hiding reason.
 */
@Service
public class StudentService {

    /** Cap on row errors embedded directly in the response envelope. */
    private static final int MAX_SAMPLE_ERRORS = 20;

    /**
     * Template header for the student CSV import. Kept next to the parser so
     * template download and parser stay in sync by construction — a new
     * importer column must be added here AND in
     * {@link #importCsv(Long, InputStream, String)}.
     */
    static final String[] IMPORT_TEMPLATE_HEADERS = {
            "externalId", "fullName", "email", "phone", "skillLevel",
            "school", "program", "classGroup", "housingAssignment"
    };

    private static final DateTimeFormatter REPORT_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final StudentRepository studentRepository;
    private final BulkImportJobRepository jobRepository;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;

    public StudentService(StudentRepository studentRepository,
                          BulkImportJobRepository jobRepository,
                          DataScopeService dataScopeService,
                          AuditService auditService) {
        this.studentRepository = studentRepository;
        this.jobRepository = jobRepository;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<StudentResponse> list(Pageable pageable) {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        Specification<Student> spec = DataScopeSpec.byFullScope(
                "organizationId", "departmentId", "facilityAreaId", scope);
        boolean raw = canViewRaw();
        return studentRepository.findAll(spec, pageable).map(s -> toResponse(s, raw));
    }

    @Transactional(readOnly = true)
    public StudentResponse get(Long id) {
        return toResponse(loadAccessible(id), canViewRaw());
    }

    /**
     * Permission gate for returning sensitive fields unmasked. Only callers
     * holding {@code students.view-raw} — typically admins and specific
     * compliance roles — can see raw phone/email/external-id. Every other
     * authenticated read returns masked values.
     */
    private boolean canViewRaw() {
        return CurrentUserResolver.current()
                .map(u -> u.permissions() != null && u.permissions().contains("students.view-raw"))
                .orElse(false);
    }

    @Transactional
    public StudentResponse create(CreateStudentRequest req, String sourceIp) {
        assertOrgAccessible(req.organizationId());

        Student s = new Student();
        s.setOrganizationId(req.organizationId());
        s.setExternalId(req.externalId());
        s.setFullName(req.fullName());
        s.setEmail(req.email());
        s.setPhone(req.phone());
        s.setDateOfBirth(req.dateOfBirth());
        s.setEmergencyContactName(req.emergencyContactName());
        s.setEmergencyContactPhone(req.emergencyContactPhone());
        s.setSkillLevel(req.skillLevel());
        s.setNotes(req.notes());
        s.setSchool(req.school());
        s.setProgram(req.program());
        s.setClassGroup(req.classGroup());
        s.setHousingAssignment(req.housingAssignment());
        s.setUserId(req.userId());
        s.setEnrollmentStatus(EnrollmentStatus.PROSPECT);

        Student saved = studentRepository.save(s);
        auditService.record(AuditAction.STUDENT_CREATED, actorId(), actorUsername(),
                "STUDENT", String.valueOf(saved.getId()),
                "Student created: " + saved.getFullName(), sourceIp);
        return toResponse(saved, canViewRaw());
    }

    @Transactional
    public StudentResponse update(Long id, UpdateStudentRequest req, String sourceIp) {
        Student s = loadAccessible(id);
        if (req.fullName() != null) s.setFullName(req.fullName());
        if (req.email() != null) s.setEmail(req.email());
        if (req.phone() != null) s.setPhone(req.phone());
        if (req.dateOfBirth() != null) s.setDateOfBirth(req.dateOfBirth());
        if (req.emergencyContactName() != null) s.setEmergencyContactName(req.emergencyContactName());
        if (req.emergencyContactPhone() != null) s.setEmergencyContactPhone(req.emergencyContactPhone());
        if (req.skillLevel() != null) s.setSkillLevel(req.skillLevel());
        if (req.notes() != null) s.setNotes(req.notes());
        if (req.enrollmentStatus() != null) {
            EnrollmentStatus prev = s.getEnrollmentStatus();
            s.setEnrollmentStatus(req.enrollmentStatus());
            if (req.enrollmentStatus() == EnrollmentStatus.ACTIVE && s.getEnrolledAt() == null) {
                s.setEnrolledAt(Instant.now());
            }
            if (prev != req.enrollmentStatus()) {
                auditService.record(AuditAction.STUDENT_STATUS_CHANGED, actorId(), actorUsername(),
                        "STUDENT", String.valueOf(s.getId()),
                        "Status " + prev + " -> " + req.enrollmentStatus(), sourceIp);
            }
        }
        if (req.school() != null) s.setSchool(req.school());
        if (req.program() != null) s.setProgram(req.program());
        if (req.classGroup() != null) s.setClassGroup(req.classGroup());
        if (req.housingAssignment() != null) s.setHousingAssignment(req.housingAssignment());
        Student saved = studentRepository.save(s);
        auditService.record(AuditAction.STUDENT_UPDATED, actorId(), actorUsername(),
                "STUDENT", String.valueOf(saved.getId()), "Student profile updated", sourceIp);
        return toResponse(saved, canViewRaw());
    }

    /**
     * Student self-profile: read own profile by looking up the Student record
     * linked to the current authenticated user's id.
     */
    @Transactional(readOnly = true)
    public StudentResponse getMyProfile() {
        Long userId = actorId();
        if (userId == null) {
            throw new BusinessException("NO_ACTOR", "Authentication required", HttpStatus.UNAUTHORIZED);
        }
        Student s = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No student profile linked to your account"));
        return toResponse(s, false);
    }

    /**
     * Student self-profile update: only permits editing a constrained set of
     * fields (phone, email, emergency contact, notes). Students cannot change
     * enrollment status, school, program, housing, or skill level — those are
     * staff-managed.
     */
    @Transactional
    public StudentResponse updateMyProfile(UpdateStudentRequest req, String sourceIp) {
        Long userId = actorId();
        if (userId == null) {
            throw new BusinessException("NO_ACTOR", "Authentication required", HttpStatus.UNAUTHORIZED);
        }
        Student s = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No student profile linked to your account"));
        // Constrained field set — students can edit contact info only
        if (req.fullName() != null) s.setFullName(req.fullName());
        if (req.email() != null) s.setEmail(req.email());
        if (req.phone() != null) s.setPhone(req.phone());
        if (req.emergencyContactName() != null) s.setEmergencyContactName(req.emergencyContactName());
        if (req.emergencyContactPhone() != null) s.setEmergencyContactPhone(req.emergencyContactPhone());
        if (req.notes() != null) s.setNotes(req.notes());
        // Students cannot change: enrollmentStatus, skillLevel, school, program, classGroup, housingAssignment
        Student saved = studentRepository.save(s);
        auditService.record(AuditAction.STUDENT_UPDATED, userId, actorUsername(),
                "STUDENT", String.valueOf(saved.getId()), "Self-profile updated", sourceIp);
        return toResponse(saved, false);
    }

    /**
     * Minimal CSV importer. Expected headers:
     * {@code externalId,fullName,email,phone,skillLevel,school,program,classGroup,housingAssignment}.
     * Rows missing the required {@code fullName} column are rejected. Rows
     * whose externalId already exists in the same organization are skipped
     * (idempotent re-run). All rejected rows are collected into a CSV error
     * report written to {@code <java.io.tmpdir>/dojostay-import-errors/} so
     * the operator can download the complete list — the API response only
     * surfaces the first {@value #MAX_SAMPLE_ERRORS} rows to keep the envelope
     * bounded on massive imports.
     */
    @Transactional
    public BulkImportResult importCsv(Long organizationId, InputStream csvInput, String sourceIp) {
        assertOrgAccessible(organizationId);

        BulkImportJob job = new BulkImportJob();
        job.setKind("STUDENTS");
        job.setOrganizationId(organizationId);
        job.setSubmittedByUserId(actorId());
        job.setStatus(BulkImportJob.Status.RUNNING);
        job = jobRepository.save(job);

        int total = 0;
        int created = 0;
        int skipped = 0;
        int failed = 0;
        List<BulkImportResult.RowError> sample = new ArrayList<>();
        List<BulkImportResult.RowError> allErrors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvInput, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new BusinessException("EMPTY_CSV", "CSV input is empty", HttpStatus.BAD_REQUEST);
            }
            Map<String, Integer> cols = indexHeader(headerLine);
            requireColumn(cols, "fullName");

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                total++;
                try {
                    String[] fields = splitCsv(line);
                    String fullName = cell(fields, cols, "fullName");
                    if (fullName == null || fullName.isBlank()) {
                        failed++;
                        recordError(sample, allErrors, lineNumber,
                                "MISSING_NAME", "fullName is required");
                        continue;
                    }
                    String externalId = cell(fields, cols, "externalId");
                    if (externalId != null && !externalId.isBlank()
                            && studentRepository
                                    .findByOrganizationIdAndExternalId(organizationId, externalId)
                                    .isPresent()) {
                        skipped++;
                        continue;
                    }

                    Student s = new Student();
                    s.setOrganizationId(organizationId);
                    s.setExternalId(externalId);
                    s.setFullName(fullName);
                    s.setEmail(cell(fields, cols, "email"));
                    s.setPhone(cell(fields, cols, "phone"));
                    s.setSkillLevel(cell(fields, cols, "skillLevel"));
                    s.setSchool(cell(fields, cols, "school"));
                    s.setProgram(cell(fields, cols, "program"));
                    s.setClassGroup(cell(fields, cols, "classGroup"));
                    s.setHousingAssignment(cell(fields, cols, "housingAssignment"));
                    s.setEnrollmentStatus(EnrollmentStatus.PROSPECT);
                    studentRepository.save(s);
                    created++;
                } catch (RuntimeException ex) {
                    failed++;
                    recordError(sample, allErrors, lineNumber, "ROW_FAILED", ex.getMessage());
                }
            }
        } catch (IOException ioe) {
            throw new BusinessException("CSV_READ_FAILED",
                    "Could not read CSV: " + ioe.getMessage(), HttpStatus.BAD_REQUEST);
        }

        String reportPath = null;
        if (!allErrors.isEmpty()) {
            reportPath = writeErrorReport(job.getId(), allErrors);
        }

        job.setTotalRows(total);
        job.setCreatedRows(created);
        job.setSkippedRows(skipped);
        job.setFailedRows(failed);
        job.setStatus(BulkImportJob.Status.COMPLETED);
        job.setCompletedAt(Instant.now());
        if (!allErrors.isEmpty()) {
            job.setErrorSummary(allErrors.size() + " row errors (see error report)");
            job.setErrorReportPath(reportPath);
        }
        BulkImportJob saved = jobRepository.save(job);

        auditService.record(AuditAction.BULK_IMPORT_RUN, actorId(), actorUsername(),
                "BULK_IMPORT_JOB", String.valueOf(saved.getId()),
                "Students import: created=" + created + " skipped=" + skipped
                        + " failed=" + failed, sourceIp);

        return new BulkImportResult(saved.getId(), total, created, skipped, failed, sample, reportPath);
    }

    /**
     * Return the canonical CSV template content for student bulk imports.
     * Controller serves this as {@code text/csv} so operators can download a
     * pre-filled header row, fill it in offline, and upload it back. The
     * template header is kept in lock-step with the parser via
     * {@link #IMPORT_TEMPLATE_HEADERS}.
     */
    public String buildImportTemplate() {
        return String.join(",", IMPORT_TEMPLATE_HEADERS) + "\n";
    }

    /**
     * Read the persisted error report for a finished import job, enforcing the
     * scope filter. Returns the CSV bytes or throws 404 if the job has no
     * errors or is not accessible to the caller.
     */
    @Transactional(readOnly = true)
    public byte[] readErrorReport(Long jobId) {
        BulkImportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Import job not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!scope.fullAccess()
                && (job.getOrganizationId() == null
                        || !scope.hasOrganization(job.getOrganizationId()))) {
            throw new NotFoundException("Import job not found");
        }
        if (job.getErrorReportPath() == null) {
            throw new NotFoundException("Import job has no error report");
        }
        try {
            return Files.readAllBytes(Paths.get(job.getErrorReportPath()));
        } catch (IOException ioe) {
            throw new BusinessException("REPORT_READ_FAILED",
                    "Could not read error report: " + ioe.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static void recordError(List<BulkImportResult.RowError> sample,
                                    List<BulkImportResult.RowError> all,
                                    int lineNumber, String code, String message) {
        BulkImportResult.RowError err = new BulkImportResult.RowError(lineNumber, code, message);
        all.add(err);
        if (sample.size() < MAX_SAMPLE_ERRORS) {
            sample.add(err);
        }
    }

    /**
     * Write the full error list to a CSV file under the tmpdir and return its
     * absolute path. Uses the job id in the filename so concurrent imports do
     * not clobber each other's reports.
     */
    private static String writeErrorReport(Long jobId, List<BulkImportResult.RowError> errors) {
        try {
            Path root = Paths.get(System.getProperty("java.io.tmpdir"), "dojostay-import-errors");
            Files.createDirectories(root);
            String fname = "student-import-" + jobId + "-"
                    + REPORT_STAMP.format(Instant.now()) + ".csv";
            Path target = root.resolve(fname);
            StringBuilder sb = new StringBuilder("lineNumber,code,message\n");
            for (BulkImportResult.RowError e : errors) {
                sb.append(e.lineNumber()).append(',')
                        .append(csvEscape(e.code())).append(',')
                        .append(csvEscape(e.message())).append('\n');
            }
            Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);
            return target.toAbsolutePath().toString();
        } catch (IOException ioe) {
            // Do not fail the whole import because we couldn't spool the error report —
            // the caller still receives the sample errors in the API response.
            return null;
        }
    }

    private static String csvEscape(String v) {
        if (v == null) return "";
        boolean needsQuote = v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0;
        String escaped = v.replace("\"", "\"\"");
        return needsQuote ? "\"" + escaped + "\"" : escaped;
    }

    private Student loadAccessible(Long id) {
        Student s = studentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Student not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (scope.fullAccess()) return s;
        if (s.getOrganizationId() == null || !scope.hasOrganization(s.getOrganizationId())) {
            throw new NotFoundException("Student not found");
        }
        return s;
    }

    private void assertOrgAccessible(Long organizationId) {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (scope.fullAccess()) return;
        if (organizationId == null || !scope.hasOrganization(organizationId)) {
            throw new BusinessException("OUT_OF_SCOPE",
                    "Target organization is not in your data scope",
                    HttpStatus.FORBIDDEN);
        }
    }

    private static Map<String, Integer> indexHeader(String header) {
        String[] cols = splitCsv(header);
        Map<String, Integer> out = new HashMap<>();
        for (int i = 0; i < cols.length; i++) {
            out.put(cols[i].trim(), i);
        }
        return out;
    }

    private static void requireColumn(Map<String, Integer> cols, String name) {
        if (!cols.containsKey(name)) {
            throw new BusinessException("MISSING_COLUMN",
                    "CSV is missing required column: " + name, HttpStatus.BAD_REQUEST);
        }
    }

    private static String cell(String[] fields, Map<String, Integer> cols, String name) {
        Integer idx = cols.get(name);
        if (idx == null || idx >= fields.length) return null;
        String v = fields[idx];
        if (v == null) return null;
        v = v.trim();
        return v.isEmpty() ? null : v;
    }

    /** Minimal CSV splitter supporting double-quoted fields with embedded commas. */
    private static String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else if (c == '"' && cur.length() == 0) {
                inQuotes = true;
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    private static Long actorId() {
        return CurrentUserResolver.current().map(CurrentUser::id).orElse(null);
    }

    private static String actorUsername() {
        return CurrentUserResolver.current().map(CurrentUser::username).orElse("system");
    }

    /**
     * Default: return masked DTO. Only callers that pass {@code raw=true}
     * (i.e. they hold {@code students.view-raw}) see unredacted sensitive
     * fields. This keeps leakage the exception, not the default.
     */
    private static StudentResponse toResponse(Student s, boolean raw) {
        String email = s.getEmail();
        String phone = s.getPhone();
        String externalId = s.getExternalId();
        String ec = s.getEmergencyContactPhone();
        if (!raw) {
            email = DataMasking.maskEmail(email);
            phone = DataMasking.maskPhone(phone);
            externalId = DataMasking.maskId(externalId);
            ec = DataMasking.maskPhone(ec);
        }
        return new StudentResponse(
                s.getId(),
                s.getUserId(),
                s.getOrganizationId(),
                externalId,
                s.getFullName(),
                email,
                phone,
                s.getDateOfBirth(),
                s.getEmergencyContactName(),
                ec,
                s.getEnrollmentStatus(),
                s.getSkillLevel(),
                s.getNotes(),
                s.getSchool(),
                s.getProgram(),
                s.getClassGroup(),
                s.getHousingAssignment(),
                s.getEnrolledAt(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                !raw
        );
    }
}
