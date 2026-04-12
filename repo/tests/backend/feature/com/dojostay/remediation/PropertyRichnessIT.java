package com.dojostay.remediation;

import com.dojostay.DojoStayApplication;
import com.dojostay.auth.CurrentUser;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.property.NightlyRateRepository;
import com.dojostay.property.PropertyAmenityRepository;
import com.dojostay.property.PropertyImageRepository;
import com.dojostay.property.PropertyRepository;
import com.dojostay.property.PropertyService;
import com.dojostay.property.RoomTypeRepository;
import com.dojostay.property.dto.CreatePropertyRequest;
import com.dojostay.property.dto.NightlyRateRequest;
import com.dojostay.property.dto.NightlyRateResponse;
import com.dojostay.property.dto.PropertyAmenityRequest;
import com.dojostay.property.dto.PropertyAmenityResponse;
import com.dojostay.property.dto.PropertyResponse;
import com.dojostay.property.dto.RoomTypeRequest;
import com.dojostay.property.dto.RoomTypeResponse;
import com.dojostay.roles.UserRoleType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * D11 — B7 property richness test.
 * Verifies: description/policies on create, amenity CRUD, room type CRUD,
 * nightly rate upsert + calendar range.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class PropertyRichnessIT {

    private static final Long ORG = 701L;

    @Autowired private PropertyRepository propertyRepo;
    @Autowired private PropertyAmenityRepository amenityRepo;
    @Autowired private PropertyImageRepository imageRepo;
    @Autowired private RoomTypeRepository roomTypeRepo;
    @Autowired private NightlyRateRepository rateRepo;
    @Autowired private PropertyService propertyService;

    @BeforeEach
    void setUp() {
        rateRepo.deleteAll();
        roomTypeRepo.deleteAll();
        imageRepo.deleteAll();
        amenityRepo.deleteAll();
        propertyRepo.deleteAll();
        authenticateAdmin();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_property_with_description_and_policies() {
        PropertyResponse p = propertyService.create(
                new CreatePropertyRequest(ORG, "TH-1", "Test Hotel",
                        "123 Main St", "A nice hotel", "No pets"),
                "127.0.0.1");
        assertEquals("A nice hotel", p.description());
        assertEquals("No pets", p.policies());
    }

    @Test
    void amenity_upsert_and_remove() {
        PropertyResponse p = propertyService.create(
                new CreatePropertyRequest(ORG, "TH-2", "Hotel 2", null, null, null),
                "127.0.0.1");

        PropertyAmenityResponse wifi = propertyService.upsertAmenity(p.id(),
                new PropertyAmenityRequest("wifi", "Wi-Fi", "wifi-icon"), "127.0.0.1");
        assertEquals("wifi", wifi.code());
        assertEquals("Wi-Fi", wifi.label());

        // Upsert updates existing
        PropertyAmenityResponse updated = propertyService.upsertAmenity(p.id(),
                new PropertyAmenityRequest("wifi", "Fast Wi-Fi", "wifi-icon-v2"), "127.0.0.1");
        assertEquals(wifi.id(), updated.id());
        assertEquals("Fast Wi-Fi", updated.label());

        // Remove
        propertyService.removeAmenity(p.id(), "wifi", "127.0.0.1");
        List<PropertyAmenityResponse> remaining = propertyService.listAmenities(p.id());
        assertEquals(0, remaining.size());
    }

    @Test
    void room_type_crud_and_duplicate_code_rejection() {
        PropertyResponse p = propertyService.create(
                new CreatePropertyRequest(ORG, "TH-3", "Hotel 3", null, null, null),
                "127.0.0.1");

        RoomTypeResponse rt = propertyService.createRoomType(p.id(),
                new RoomTypeRequest("STD", "Standard", "Basic room", 2, 5000, "TV, AC"),
                "127.0.0.1");
        assertEquals("STD", rt.code());
        assertEquals(5000, rt.baseRateCents());

        // Duplicate code rejected
        var ex = assertThrows(BusinessException.class, () ->
                propertyService.createRoomType(p.id(),
                        new RoomTypeRequest("STD", "Standard 2", null, 1, 3000, null),
                        "127.0.0.1"));
        assertEquals("DUPLICATE_ROOM_TYPE", ex.getCode());

        // Update works
        RoomTypeResponse updated = propertyService.updateRoomType(rt.id(),
                new RoomTypeRequest("STD", "Standard Deluxe", "Upgraded", 3, 7500, "TV, AC, Minibar"),
                "127.0.0.1");
        assertEquals("Standard Deluxe", updated.name());
        assertEquals(7500, updated.baseRateCents());
    }

    @Test
    void nightly_rate_upsert_and_calendar_range() {
        PropertyResponse p = propertyService.create(
                new CreatePropertyRequest(ORG, "TH-4", "Hotel 4", null, null, null),
                "127.0.0.1");
        RoomTypeResponse rt = propertyService.createRoomType(p.id(),
                new RoomTypeRequest("DLX", "Deluxe", null, 2, 10000, null),
                "127.0.0.1");

        LocalDate date1 = LocalDate.of(2026, 7, 1);
        LocalDate date2 = LocalDate.of(2026, 7, 2);

        NightlyRateResponse r1 = propertyService.upsertNightlyRate(rt.id(),
                new NightlyRateRequest(date1, 12000, 5), "127.0.0.1");
        assertNotNull(r1.id());
        assertEquals(12000, r1.rateCents());

        // Upsert same date updates in place
        NightlyRateResponse r1u = propertyService.upsertNightlyRate(rt.id(),
                new NightlyRateRequest(date1, 11000, 3), "127.0.0.1");
        assertEquals(r1.id(), r1u.id());
        assertEquals(11000, r1u.rateCents());
        assertEquals(3, r1u.availableCount());

        propertyService.upsertNightlyRate(rt.id(),
                new NightlyRateRequest(date2, 9000, 8), "127.0.0.1");

        List<NightlyRateResponse> range = propertyService.listNightlyRates(
                rt.id(), date1, date2);
        assertEquals(2, range.size());
    }

    private static void authenticateAdmin() {
        CurrentUser admin = new CurrentUser(1L, "root-admin", "Root Admin",
                UserRoleType.ADMIN, Set.of("ADMIN"),
                Set.of("property.read", "property.write"));
        var auth = new UsernamePasswordAuthenticationToken(
                admin, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("property.read"),
                        new SimpleGrantedAuthority("property.write")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
