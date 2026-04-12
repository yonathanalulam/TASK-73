package com.dojostay.credentials;

import com.dojostay.common.ApiResponse;
import com.dojostay.credentials.dto.BlacklistUserRequest;
import com.dojostay.credentials.dto.CredentialReviewResponse;
import com.dojostay.credentials.dto.DecideCredentialReviewRequest;
import com.dojostay.credentials.dto.SubmitCredentialReviewRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/credentials")
public class CredentialReviewController {

    private final CredentialReviewService service;

    public CredentialReviewController(CredentialReviewService service) {
        this.service = service;
    }

    @GetMapping("/reviews")
    @PreAuthorize("hasAuthority('credentials.review')")
    public ApiResponse<List<CredentialReviewResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    /**
     * Legacy JSON submission — no evidence file.
     */
    @PostMapping("/reviews")
    @PreAuthorize("hasAuthority('credentials.review')")
    public ApiResponse<CredentialReviewResponse> submit(
            @Valid @RequestBody SubmitCredentialReviewRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(service.submit(req, clientIp(httpReq)));
    }

    /**
     * Multipart submission. Form parts:
     * <ul>
     *   <li>{@code file} — the evidence file (pdf/jpeg/png)</li>
     *   <li>{@code studentId}, {@code discipline}, {@code requestedRank},
     *       {@code currentRank}, {@code evidence} — the review fields as form
     *       fields</li>
     * </ul>
     */
    @PostMapping(value = "/reviews/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('credentials.review')")
    public ApiResponse<CredentialReviewResponse> submitWithFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam("studentId") @NotNull Long studentId,
            @RequestParam("discipline") @NotBlank @Size(max = 64) String discipline,
            @RequestParam("requestedRank") @NotBlank @Size(max = 64) String requestedRank,
            @RequestParam(value = "currentRank", required = false) @Size(max = 64) String currentRank,
            @RequestParam(value = "evidence", required = false) @Size(max = 2000) String evidence,
            HttpServletRequest httpReq) {
        SubmitCredentialReviewRequest req = new SubmitCredentialReviewRequest(
                studentId, discipline, requestedRank, currentRank, evidence);
        return ApiResponse.ok(service.submitWithFile(req, file, clientIp(httpReq)));
    }

    @PostMapping("/reviews/{id}/decide")
    @PreAuthorize("hasAuthority('credentials.review')")
    public ApiResponse<CredentialReviewResponse> decide(
            @PathVariable Long id,
            @Valid @RequestBody DecideCredentialReviewRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(service.decide(id, req, clientIp(httpReq)));
    }

    /**
     * Blacklist the user account behind the review's student. Separate from
     * reject-decision so a review can be rejected without blacklisting, and
     * so a blacklist can be applied independently of the review outcome when
     * a duplicate was caught at submit time.
     */
    @PostMapping("/reviews/{id}/blacklist")
    @PreAuthorize("hasAuthority('credentials.review')")
    public ApiResponse<Void> blacklist(
            @PathVariable Long id,
            @Valid @RequestBody BlacklistUserRequest req,
            HttpServletRequest httpReq) {
        service.blacklistSubject(id, req.reason(), clientIp(httpReq));
        return ApiResponse.empty();
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
