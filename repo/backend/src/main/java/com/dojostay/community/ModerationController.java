package com.dojostay.community;

import com.dojostay.common.ApiResponse;
import com.dojostay.community.dto.CreateModerationReportRequest;
import com.dojostay.community.dto.ModerationReportResponse;
import com.dojostay.community.dto.ResolveReportRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/moderation")
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('moderation.review')")
    public ApiResponse<List<ModerationReportResponse>> listOpen() {
        return ApiResponse.ok(moderationService.listOpen());
    }

    @PostMapping("/reports")
    @PreAuthorize("hasAuthority('community.write')")
    public ApiResponse<ModerationReportResponse> file(
            @Valid @RequestBody CreateModerationReportRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(moderationService.file(req, clientIp(httpReq)));
    }

    @PostMapping("/reports/{id}/resolve")
    @PreAuthorize("hasAuthority('moderation.review')")
    public ApiResponse<ModerationReportResponse> resolve(
            @PathVariable Long id,
            @Valid @RequestBody ResolveReportRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(moderationService.resolve(id, req, clientIp(httpReq)));
    }

    @PostMapping("/restore")
    @PreAuthorize("hasAuthority('moderation.review')")
    public ApiResponse<Void> restore(
            @RequestParam ModerationReport.TargetType targetType,
            @RequestParam Long targetId,
            HttpServletRequest httpReq) {
        moderationService.restore(targetType, targetId, clientIp(httpReq));
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
