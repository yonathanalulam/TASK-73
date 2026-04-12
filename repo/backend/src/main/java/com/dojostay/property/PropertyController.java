package com.dojostay.property;

import com.dojostay.common.ApiResponse;
import com.dojostay.property.dto.AvailabilityResponse;
import com.dojostay.property.dto.CreatePropertyRequest;
import com.dojostay.property.dto.CreateReservationRequest;
import com.dojostay.property.dto.NightlyRateRequest;
import com.dojostay.property.dto.NightlyRateResponse;
import com.dojostay.property.dto.PropertyAmenityRequest;
import com.dojostay.property.dto.PropertyAmenityResponse;
import com.dojostay.property.dto.PropertyImageResponse;
import com.dojostay.property.dto.PropertyResponse;
import com.dojostay.property.dto.ReservationResponse;
import com.dojostay.property.dto.RoomTypeRequest;
import com.dojostay.property.dto.RoomTypeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/property")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('property.read')")
    public ApiResponse<List<PropertyResponse>> list() {
        return ApiResponse.ok(propertyService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('property.write')")
    public ApiResponse<PropertyResponse> create(@Valid @RequestBody CreatePropertyRequest req,
                                                HttpServletRequest httpReq) {
        return ApiResponse.ok(propertyService.create(req, clientIp(httpReq)));
    }

    @GetMapping("/{id}/availability")
    @PreAuthorize("hasAuthority('property.read')")
    public ApiResponse<AvailabilityResponse> availability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startsOn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endsOn) {
        return ApiResponse.ok(propertyService.availability(id, startsOn, endsOn));
    }

    @PostMapping("/reservations")
    @PreAuthorize("hasAuthority('reservations.write')")
    public ApiResponse<ReservationResponse> reserve(
            @Valid @RequestBody CreateReservationRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(propertyService.reserve(req, clientIp(httpReq)));
    }

    @DeleteMapping("/reservations/{id}")
    @PreAuthorize("hasAuthority('reservations.write')")
    public ApiResponse<ReservationResponse> cancel(@PathVariable Long id,
                                                   HttpServletRequest httpReq) {
        return ApiResponse.ok(propertyService.cancel(id, clientIp(httpReq)));
    }

    // ---- B7: amenities ---------------------------------------------------

    @GetMapping("/{id}/amenities")
    @PreAuthorize("hasAuthority('property.read')")
    public ApiResponse<List<PropertyAmenityResponse>> listAmenities(@PathVariable Long id) {
        return ApiResponse.ok(propertyService.listAmenities(id));
    }

    @PutMapping("/{id}/amenities")
    @PreAuthorize("hasAuthority('property.write')")
    public ApiResponse<PropertyAmenityResponse> upsertAmenity(
            @PathVariable Long id,
            @Valid @RequestBody PropertyAmenityRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(propertyService.upsertAmenity(id, req, clientIp(httpReq)));
    }

    @DeleteMapping("/{id}/amenities/{code}")
    @PreAuthorize("hasAuthority('property.write')")
    public ApiResponse<Void> removeAmenity(@PathVariable Long id,
                                           @PathVariable String code,
                                           HttpServletRequest httpReq) {
        propertyService.removeAmenity(id, code, clientIp(httpReq));
        return ApiResponse.empty();
    }

    // ---- B7: images ------------------------------------------------------

    @GetMapping("/{id}/images")
    @PreAuthorize("hasAuthority('property.read')")
    public ApiResponse<List<PropertyImageResponse>> listImages(@PathVariable Long id) {
        return ApiResponse.ok(propertyService.listImages(id));
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('property.write')")
    public ApiResponse<PropertyImageResponse> uploadImage(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "displayOrder", required = false) Integer displayOrder,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(propertyService.uploadImage(id, file, caption, displayOrder, clientIp(httpReq)));
    }

    @DeleteMapping("/images/{imageId}")
    @PreAuthorize("hasAuthority('property.write')")
    public ApiResponse<Void> deleteImage(@PathVariable Long imageId,
                                         HttpServletRequest httpReq) {
        propertyService.deleteImage(imageId, clientIp(httpReq));
        return ApiResponse.empty();
    }

    // ---- B7: room types --------------------------------------------------

    @GetMapping("/{id}/room-types")
    @PreAuthorize("hasAuthority('property.read')")
    public ApiResponse<List<RoomTypeResponse>> listRoomTypes(@PathVariable Long id) {
        return ApiResponse.ok(propertyService.listRoomTypes(id));
    }

    @PostMapping("/{id}/room-types")
    @PreAuthorize("hasAuthority('property.write')")
    public ApiResponse<RoomTypeResponse> createRoomType(
            @PathVariable Long id,
            @Valid @RequestBody RoomTypeRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(propertyService.createRoomType(id, req, clientIp(httpReq)));
    }

    @PutMapping("/room-types/{roomTypeId}")
    @PreAuthorize("hasAuthority('property.write')")
    public ApiResponse<RoomTypeResponse> updateRoomType(
            @PathVariable Long roomTypeId,
            @Valid @RequestBody RoomTypeRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(propertyService.updateRoomType(roomTypeId, req, clientIp(httpReq)));
    }

    // ---- B7: nightly rate calendar ---------------------------------------

    @GetMapping("/room-types/{roomTypeId}/rates")
    @PreAuthorize("hasAuthority('property.read')")
    public ApiResponse<List<NightlyRateResponse>> listNightlyRates(
            @PathVariable Long roomTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(propertyService.listNightlyRates(roomTypeId, from, to));
    }

    @PutMapping("/room-types/{roomTypeId}/rates")
    @PreAuthorize("hasAuthority('property.write')")
    public ApiResponse<NightlyRateResponse> upsertNightlyRate(
            @PathVariable Long roomTypeId,
            @Valid @RequestBody NightlyRateRequest req,
            HttpServletRequest httpReq) {
        return ApiResponse.ok(propertyService.upsertNightlyRate(roomTypeId, req, clientIp(httpReq)));
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
