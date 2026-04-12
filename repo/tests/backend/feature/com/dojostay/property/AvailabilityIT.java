package com.dojostay.property;

import com.dojostay.DojoStayApplication;
import com.dojostay.auth.CurrentUser;
import com.dojostay.property.dto.AvailabilityResponse;
import com.dojostay.property.dto.CreateReservationRequest;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the core Phase 4 guarantee: a bed that already has a non-cancelled
 * reservation overlapping a requested window is reported as unavailable and a
 * subsequent reservation attempt is rejected with {@code BED_UNAVAILABLE}.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class AvailabilityIT {

    @Autowired private PropertyRepository propertyRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BedRepository bedRepository;
    @Autowired private LodgingReservationRepository reservationRepository;
    @Autowired private PropertyService propertyService;

    private static final Long ORG_A = 500L;

    private Property property;
    private Bed bedOne;
    private Bed bedTwo;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        bedRepository.deleteAll();
        roomRepository.deleteAll();
        propertyRepository.deleteAll();

        property = new Property();
        property.setOrganizationId(ORG_A);
        property.setCode("HQ-1");
        property.setName("HQ Dorm");
        propertyRepository.save(property);

        Room room = new Room();
        room.setPropertyId(property.getId());
        room.setCode("101");
        room.setName("Room 101");
        room.setCapacity(2);
        roomRepository.save(room);

        bedOne = new Bed();
        bedOne.setRoomId(room.getId());
        bedOne.setCode("A");
        bedOne.setLabel("Bed A");
        bedRepository.save(bedOne);

        bedTwo = new Bed();
        bedTwo.setRoomId(room.getId());
        bedTwo.setCode("B");
        bedTwo.setLabel("Bed B");
        bedRepository.save(bedTwo);

        authenticateAdmin();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void all_beds_start_as_available() {
        AvailabilityResponse resp = propertyService.availability(
                property.getId(),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 5));

        assertEquals(2, resp.beds().size());
        assertTrue(resp.beds().stream().allMatch(AvailabilityResponse.BedAvailability::available));
    }

    @Test
    void reserved_bed_is_unavailable_for_overlapping_window_only() {
        propertyService.reserve(new CreateReservationRequest(
                bedOne.getId(), "Alice", LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 5), null, null), "127.0.0.1");

        // Overlap: 5/3 is inside [5/1, 5/5)
        AvailabilityResponse overlap = propertyService.availability(
                property.getId(),
                LocalDate.of(2026, 5, 3),
                LocalDate.of(2026, 5, 4));
        var bedOneEntry = overlap.beds().stream()
                .filter(b -> b.bedId().equals(bedOne.getId())).findFirst().orElseThrow();
        assertFalse(bedOneEntry.available(), "bedOne must be unavailable during overlap");

        var bedTwoEntry = overlap.beds().stream()
                .filter(b -> b.bedId().equals(bedTwo.getId())).findFirst().orElseThrow();
        assertTrue(bedTwoEntry.available(), "bedTwo must stay available — it was never reserved");

        // Adjacent range [5/5, 5/6) does not overlap — endsOn is exclusive.
        AvailabilityResponse adjacent = propertyService.availability(
                property.getId(),
                LocalDate.of(2026, 5, 5),
                LocalDate.of(2026, 5, 6));
        assertTrue(adjacent.beds().stream()
                .filter(b -> b.bedId().equals(bedOne.getId()))
                .findFirst().orElseThrow().available(),
                "Range adjacent to the existing reservation should be free");
    }

    @Test
    void second_reservation_in_overlapping_window_is_rejected() {
        propertyService.reserve(new CreateReservationRequest(
                bedOne.getId(), "Alice", LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 5), null, null), "127.0.0.1");

        var ex = assertThrows(com.dojostay.common.exception.BusinessException.class, () ->
                propertyService.reserve(new CreateReservationRequest(
                        bedOne.getId(), "Bob", LocalDate.of(2026, 5, 2),
                        LocalDate.of(2026, 5, 4), null, null), "127.0.0.1"));
        assertEquals("BED_UNAVAILABLE", ex.getCode());
    }

    private static void authenticateAdmin() {
        CurrentUser admin = new CurrentUser(1L, "root-admin", "Root Admin",
                UserRoleType.ADMIN, Set.of("ADMIN"),
                Set.of("property.read", "property.write",
                        "reservations.read", "reservations.write"));
        var auth = new UsernamePasswordAuthenticationToken(
                admin, "n/a",
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("property.read"),
                        new SimpleGrantedAuthority("property.write"),
                        new SimpleGrantedAuthority("reservations.read"),
                        new SimpleGrantedAuthority("reservations.write")
                )
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
