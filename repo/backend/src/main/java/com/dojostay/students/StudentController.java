package com.dojostay.students;

import com.dojostay.common.ApiResponse;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.students.dto.BulkImportResult;
import com.dojostay.students.dto.CreateStudentRequest;
import com.dojostay.students.dto.StudentResponse;
import com.dojostay.students.dto.UpdateStudentRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * /api/students — list, CRUD, and CSV bulk import. All endpoints are gated by
 * permission code; the service additionally enforces the data-scope filter.
 */
@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    /** Student self-service: read own profile. */
    @GetMapping("/me")
    @PreAuthorize("hasAuthority('students.self.read')")
    public ApiResponse<StudentResponse> myProfile() {
        return ApiResponse.ok(studentService.getMyProfile());
    }

    /** Student self-service: update own allowed fields. */
    @PutMapping("/me")
    @PreAuthorize("hasAuthority('students.self.write')")
    public ApiResponse<StudentResponse> updateMyProfile(@Valid @RequestBody UpdateStudentRequest req,
                                                        HttpServletRequest httpReq) {
        return ApiResponse.ok(studentService.updateMyProfile(req, clientIp(httpReq)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('students.read')")
    public ApiResponse<Page<StudentResponse>> list(Pageable pageable) {
        return ApiResponse.ok(studentService.list(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('students.read')")
    public ApiResponse<StudentResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(studentService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('students.write')")
    public ApiResponse<StudentResponse> create(@Valid @RequestBody CreateStudentRequest req,
                                               HttpServletRequest httpReq) {
        return ApiResponse.ok(studentService.create(req, clientIp(httpReq)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('students.write')")
    public ApiResponse<StudentResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateStudentRequest req,
                                               HttpServletRequest httpReq) {
        return ApiResponse.ok(studentService.update(id, req, clientIp(httpReq)));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('students.import')")
    public ApiResponse<BulkImportResult> importCsv(
            @RequestParam("organizationId") Long organizationId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpReq) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "CSV file is required",
                    HttpStatus.BAD_REQUEST);
        }
        try {
            return ApiResponse.ok(studentService.importCsv(
                    organizationId, file.getInputStream(), clientIp(httpReq)));
        } catch (IOException ioe) {
            throw new BusinessException("CSV_READ_FAILED",
                    "Could not read upload: " + ioe.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Download the CSV template for student bulk imports. Served as a raw
     * text/csv so the client browser saves it with a {@code .csv} extension
     * directly. Gated on the import permission so non-importers can't probe
     * the expected header shape.
     */
    @GetMapping(value = "/import/template", produces = "text/csv")
    @PreAuthorize("hasAuthority('students.import')")
    public ResponseEntity<byte[]> importTemplate() {
        byte[] body = studentService.buildImportTemplate().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"student-import-template.csv\"")
                .body(body);
    }

    /**
     * Download the complete error report for a finished import job. The body
     * is the same error rows the service wrote to disk during the import run;
     * the sample embedded in the import response is capped at 20 rows.
     */
    @GetMapping(value = "/import/{jobId}/errors", produces = "text/csv")
    @PreAuthorize("hasAuthority('students.import')")
    public ResponseEntity<byte[]> importErrors(@PathVariable Long jobId) {
        byte[] body = studentService.readErrorReport(jobId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"student-import-" + jobId + "-errors.csv\"")
                .body(body);
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return req.getRemoteAddr();
    }
}
