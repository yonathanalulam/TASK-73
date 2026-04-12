package com.dojostay.risk;

import com.dojostay.common.ApiResponse;
import com.dojostay.risk.dto.ClearRiskFlagRequest;
import com.dojostay.risk.dto.IncidentResponse;
import com.dojostay.risk.dto.LogIncidentRequest;
import com.dojostay.risk.dto.RaiseRiskFlagRequest;
import com.dojostay.risk.dto.RiskFlagResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/risk")
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @GetMapping("/flags")
    @PreAuthorize("hasAuthority('risk.read')")
    public ApiResponse<List<RiskFlagResponse>> listFlags() {
        return ApiResponse.ok(riskService.listFlags());
    }

    @PostMapping("/flags")
    @PreAuthorize("hasAuthority('risk.write')")
    public ApiResponse<RiskFlagResponse> raise(@Valid @RequestBody RaiseRiskFlagRequest req,
                                               HttpServletRequest httpReq) {
        return ApiResponse.ok(riskService.raiseFlag(req, clientIp(httpReq)));
    }

    @PostMapping("/flags/{id}/clear")
    @PreAuthorize("hasAuthority('risk.write')")
    public ApiResponse<RiskFlagResponse> clear(@PathVariable Long id,
                                               @Valid @RequestBody ClearRiskFlagRequest req,
                                               HttpServletRequest httpReq) {
        return ApiResponse.ok(riskService.clearFlag(id, req, clientIp(httpReq)));
    }

    @GetMapping("/incidents")
    @PreAuthorize("hasAuthority('risk.read')")
    public ApiResponse<List<IncidentResponse>> listIncidents() {
        return ApiResponse.ok(riskService.listIncidents());
    }

    @PostMapping("/incidents")
    @PreAuthorize("hasAuthority('risk.write')")
    public ApiResponse<IncidentResponse> logIncident(@Valid @RequestBody LogIncidentRequest req,
                                                     HttpServletRequest httpReq) {
        return ApiResponse.ok(riskService.logIncident(req, clientIp(httpReq)));
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
