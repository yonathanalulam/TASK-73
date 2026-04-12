package com.dojostay.training;

import com.dojostay.common.ApiResponse;
import com.dojostay.training.dto.CreateTrainingClassRequest;
import com.dojostay.training.dto.CreateTrainingSessionRequest;
import com.dojostay.training.dto.TrainingClassResponse;
import com.dojostay.training.dto.TrainingSessionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/training")
public class TrainingController {

    private final TrainingService trainingService;

    public TrainingController(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    @GetMapping("/classes")
    @PreAuthorize("hasAuthority('training.read')")
    public ApiResponse<List<TrainingClassResponse>> listClasses() {
        return ApiResponse.ok(trainingService.listClasses());
    }

    @PostMapping("/classes")
    @PreAuthorize("hasAuthority('training.write')")
    public ApiResponse<TrainingClassResponse> createClass(
            @Valid @RequestBody CreateTrainingClassRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(trainingService.createClass(req, clientIp(httpReq)));
    }

    @GetMapping("/classes/{id}/sessions")
    @PreAuthorize("hasAuthority('training.read')")
    public ApiResponse<List<TrainingSessionResponse>> listSessions(@PathVariable Long id) {
        return ApiResponse.ok(trainingService.listSessionsForClass(id));
    }

    /**
     * Sparring/matching filter endpoint. All query params are optional; any
     * combination narrows the search. Used by the training browse / sparring
     * partner picker UI.
     */
    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('training.read')")
    public ApiResponse<List<TrainingSessionResponse>> filterSessions(
            @RequestParam(required = false) TrainingSession.SessionType sessionType,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Integer weightClassLbs,
            @RequestParam(required = false) String style,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.ok(trainingService.filterSessions(
                sessionType, level, weightClassLbs, style, from, to));
    }

    @PostMapping("/sessions")
    @PreAuthorize("hasAuthority('training.write')")
    public ApiResponse<TrainingSessionResponse> createSession(
            @Valid @RequestBody CreateTrainingSessionRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(trainingService.createSession(req, clientIp(httpReq)));
    }

    @DeleteMapping("/sessions/{id}")
    @PreAuthorize("hasAuthority('training.write')")
    public ApiResponse<TrainingSessionResponse> cancelSession(@PathVariable Long id,
                                                              HttpServletRequest httpReq) {
        return ApiResponse.ok(trainingService.cancelSession(id, clientIp(httpReq)));
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
