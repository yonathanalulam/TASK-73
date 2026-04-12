package com.dojostay.training;

import com.dojostay.common.ApiResponse;
import com.dojostay.training.dto.BookingResponse;
import com.dojostay.training.dto.CreateBookingRequest;
import com.dojostay.training.dto.RefundBookingRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * My bookings: returns bookings for the student record linked to the
     * currently authenticated user. Used by student self-service UI.
     */
    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('bookings.read')")
    public ApiResponse<List<BookingResponse>> myBookings() {
        return ApiResponse.ok(bookingService.listForCurrentUser());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('bookings.read')")
    public ApiResponse<List<BookingResponse>> list(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Long studentId) {
        if (sessionId != null) {
            return ApiResponse.ok(bookingService.listForSession(sessionId));
        }
        if (studentId != null) {
            return ApiResponse.ok(bookingService.listForStudent(studentId));
        }
        return ApiResponse.ok(List.of());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('bookings.write')")
    public ApiResponse<BookingResponse> book(@Valid @RequestBody CreateBookingRequest req,
                                             HttpServletRequest httpReq) {
        return ApiResponse.ok(bookingService.book(req, clientIp(httpReq)));
    }

    /**
     * INITIATED → CONFIRMED transition. Separate endpoint rather than a
     * generic status-PATCH so Spring Security can gate it distinctly if we
     * later split the confirm permission from the plain write permission.
     */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('bookings.write')")
    public ApiResponse<BookingResponse> confirm(@PathVariable Long id,
                                                HttpServletRequest httpReq) {
        return ApiResponse.ok(bookingService.confirm(id, clientIp(httpReq)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('bookings.write')")
    public ApiResponse<BookingResponse> cancel(@PathVariable Long id,
                                               HttpServletRequest httpReq) {
        return ApiResponse.ok(bookingService.cancel(id, clientIp(httpReq)));
    }

    /**
     * Post a credit-ledger refund for a cancelled booking and move it to
     * REFUNDED. Refunds are ALWAYS internal credit — never cash — so the
     * request body only carries the credit amount and an optional note.
     */
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('bookings.write')")
    public ApiResponse<BookingResponse> refund(@PathVariable Long id,
                                               @Valid @RequestBody RefundBookingRequest req,
                                               HttpServletRequest httpReq) {
        return ApiResponse.ok(bookingService.refund(
                id, req.creditAmount(), req.notes(), clientIp(httpReq)));
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
