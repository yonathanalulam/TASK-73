package com.dojostay.training;

import com.dojostay.common.ApiResponse;
import com.dojostay.training.dto.CreditAdjustmentRequest;
import com.dojostay.training.dto.CreditBalanceResponse;
import com.dojostay.training.dto.CreditTransactionResponse;
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
@RequestMapping("/api/credits")
public class CreditController {

    private final CreditService creditService;

    public CreditController(CreditService creditService) {
        this.creditService = creditService;
    }

    @GetMapping("/students/{studentId}/balance")
    @PreAuthorize("hasAuthority('credits.read')")
    public ApiResponse<CreditBalanceResponse> balance(@PathVariable Long studentId) {
        return ApiResponse.ok(creditService.getBalance(studentId));
    }

    @GetMapping("/students/{studentId}/history")
    @PreAuthorize("hasAuthority('credits.read')")
    public ApiResponse<List<CreditTransactionResponse>> history(@PathVariable Long studentId) {
        return ApiResponse.ok(creditService.history(studentId));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAuthority('credits.write')")
    public ApiResponse<CreditTransactionResponse> adjust(
            @Valid @RequestBody CreditAdjustmentRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(creditService.adjust(req, clientIp(httpReq)));
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
