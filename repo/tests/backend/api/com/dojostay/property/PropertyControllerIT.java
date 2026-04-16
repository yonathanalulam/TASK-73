package com.dojostay.property;

import com.dojostay.DojoStayApplication;
import com.dojostay.audit.AuditLogRepository;
import com.dojostay.auth.CurrentUser;
import com.dojostay.organizations.Organization;
import com.dojostay.organizations.OrganizationRepository;
import com.dojostay.roles.Role;
import com.dojostay.roles.RoleRepository;
import com.dojostay.roles.UserRoleType;
import com.dojostay.scopes.DataScopeRuleRepository;
import com.dojostay.users.User;
import com.dojostay.users.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP API test for /api/property — covers property CRUD, availability,
 * reservations, amenities, images, room types, and nightly rates.
 */
@SpringBootTest(classes = DojoStayApplication.class)
@ActiveProfiles("test")
class PropertyControllerIT {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BedRepository bedRepository;
    @Autowired private PropertyAmenityRepository amenityRepository;
    @Autowired private PropertyImageRepository imageRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private NightlyRateRepository nightlyRateRepository;
    @Autowired private LodgingReservationRepository reservationRepository;
    @Autowired private DataScopeRuleRepository scopeRuleRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long adminId;
    private Long orgId;
    private Long propertyId;
    private Long roomId;
    private Long bedId;
    private Long roomTypeId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        auditLogRepository.deleteAll();
        nightlyRateRepository.deleteAll();
        roomTypeRepository.deleteAll();
        imageRepository.deleteAll();
        amenityRepository.deleteAll();
        reservationRepository.deleteAll();
        bedRepository.deleteAll();
        roomRepository.deleteAll();
        propertyRepository.deleteAll();
        scopeRuleRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        organizationRepository.deleteAll();

        Organization org = new Organization();
        org.setCode("PROP-ORG");
        org.setName("Property Test Org");
        organizationRepository.save(org);
        orgId = org.getId();

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        adminRole.setDisplayName("Administrator");
        roleRepository.save(adminRole);

        User admin = new User();
        admin.setUsername("prop-admin");
        admin.setFullName("Property Admin");
        admin.setPasswordHash(passwordEncoder.encode("Seeded-Pass!9"));
        admin.setPrimaryRole(UserRoleType.ADMIN);
        admin.setEnabled(true);
        admin.setRoles(new HashSet<>(Set.of(adminRole)));
        userRepository.save(admin);
        adminId = admin.getId();

        Property prop = new Property();
        prop.setOrganizationId(orgId);
        prop.setCode("PROP-1");
        prop.setName("Test Property");
        prop.setAddress("123 Test St");
        propertyRepository.save(prop);
        propertyId = prop.getId();

        Room room = new Room();
        room.setPropertyId(propertyId);
        room.setCode("R-1");
        room.setName("Room One");
        room.setCapacity(2);
        roomRepository.save(room);
        roomId = room.getId();

        Bed bed = new Bed();
        bed.setRoomId(roomId);
        bed.setCode("B-1");
        bed.setLabel("Bed One");
        bedRepository.save(bed);
        bedId = bed.getId();

        RoomType rt = new RoomType();
        rt.setPropertyId(propertyId);
        rt.setCode("RT-STD");
        rt.setName("Standard");
        rt.setMaxOccupancy(2);
        rt.setBaseRateCents(5000);
        roomTypeRepository.save(rt);
        roomTypeId = rt.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---- Property CRUD ----

    @Test
    void list_properties_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/property"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_properties_without_permission_returns_403() throws Exception {
        mockMvc.perform(get("/api/property").with(authentication(noPermsAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_properties_as_admin_returns_properties() throws Exception {
        mockMvc.perform(get("/api/property").with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].code").value("PROP-1"))
                .andExpect(jsonPath("$.data[0].name").value("Test Property"));
    }

    @Test
    void create_property_as_admin_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("organizationId", orgId);
        body.put("code", "PROP-NEW");
        body.put("name", "New Property");
        body.put("address", "456 New St");

        mockMvc.perform(post("/api/property")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("PROP-NEW"))
                .andExpect(jsonPath("$.data.name").value("New Property"));
    }

    @Test
    void create_property_without_permission_returns_403() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("organizationId", orgId);
        body.put("code", "FAIL");
        body.put("name", "Fail");

        mockMvc.perform(post("/api/property")
                        .with(authentication(noPermsAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    // ---- Availability ----

    @Test
    void availability_returns_bed_info() throws Exception {
        mockMvc.perform(get("/api/property/" + propertyId + "/availability")
                        .param("startsOn", "2026-08-01")
                        .param("endsOn", "2026-08-05")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.propertyId").value(propertyId))
                .andExpect(jsonPath("$.data.beds").isArray());
    }

    // ---- Reservations ----

    @Test
    void create_reservation_as_admin_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("bedId", bedId);
        body.put("guestName", "Test Guest");
        body.put("startsOn", "2026-09-01");
        body.put("endsOn", "2026-09-05");

        mockMvc.perform(post("/api/property/reservations")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.guestName").value("Test Guest"))
                .andExpect(jsonPath("$.data.status").value("BOOKED"));
    }

    @Test
    void create_reservation_without_permission_returns_403() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("bedId", bedId);
        body.put("guestName", "No Perm");
        body.put("startsOn", "2026-09-01");
        body.put("endsOn", "2026-09-05");

        mockMvc.perform(post("/api/property/reservations")
                        .with(authentication(noPermsAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_reservation_as_admin_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("bedId", bedId);
        body.put("guestName", "Cancel Guest");
        body.put("startsOn", "2026-10-01");
        body.put("endsOn", "2026-10-05");

        String resp = mockMvc.perform(post("/api/property/reservations")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long resId = objectMapper.readTree(resp).path("data").path("id").asLong();

        mockMvc.perform(delete("/api/property/reservations/" + resId)
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    // ---- Amenities ----

    @Test
    void list_amenities_returns_empty_initially() throws Exception {
        mockMvc.perform(get("/api/property/" + propertyId + "/amenities")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void upsert_amenity_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("code", "WIFI");
        body.put("label", "Free Wi-Fi");
        body.put("icon", "wifi");

        mockMvc.perform(put("/api/property/" + propertyId + "/amenities")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("WIFI"))
                .andExpect(jsonPath("$.data.label").value("Free Wi-Fi"));
    }

    @Test
    void remove_amenity_succeeds() throws Exception {
        PropertyAmenity a = new PropertyAmenity();
        a.setPropertyId(propertyId);
        a.setCode("POOL");
        a.setLabel("Swimming Pool");
        amenityRepository.save(a);

        mockMvc.perform(delete("/api/property/" + propertyId + "/amenities/POOL")
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---- Images ----

    @Test
    void list_images_returns_list() throws Exception {
        mockMvc.perform(get("/api/property/" + propertyId + "/images")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void upload_image_succeeds() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/property/" + propertyId + "/images")
                        .file(file)
                        .param("caption", "Front view")
                        .param("displayOrder", "1")
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.caption").value("Front view"));
    }

    @Test
    void delete_image_succeeds() throws Exception {
        PropertyImage img = new PropertyImage();
        img.setPropertyId(propertyId);
        img.setStoragePath("/tmp/test.jpg");
        img.setCaption("Delete Me");
        img.setDisplayOrder(1);
        imageRepository.save(img);

        mockMvc.perform(delete("/api/property/images/" + img.getId())
                        .with(authentication(adminAuth())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---- Room Types ----

    @Test
    void list_room_types_returns_types() throws Exception {
        mockMvc.perform(get("/api/property/" + propertyId + "/room-types")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].code").value("RT-STD"));
    }

    @Test
    void create_room_type_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("code", "RT-DLX");
        body.put("name", "Deluxe");
        body.put("maxOccupancy", 4);
        body.put("baseRateCents", 10000);

        mockMvc.perform(post("/api/property/" + propertyId + "/room-types")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value("RT-DLX"))
                .andExpect(jsonPath("$.data.baseRateCents").value(10000));
    }

    @Test
    void update_room_type_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("code", "RT-STD");
        body.put("name", "Standard Updated");
        body.put("maxOccupancy", 3);
        body.put("baseRateCents", 6000);

        mockMvc.perform(put("/api/property/room-types/" + roomTypeId)
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Standard Updated"))
                .andExpect(jsonPath("$.data.baseRateCents").value(6000));
    }

    // ---- Nightly Rates ----

    @Test
    void list_nightly_rates_returns_list() throws Exception {
        mockMvc.perform(get("/api/property/room-types/" + roomTypeId + "/rates")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void upsert_nightly_rate_succeeds() throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("stayDate", "2026-08-15");
        body.put("rateCents", 7500);
        body.put("availableCount", 3);

        mockMvc.perform(put("/api/property/room-types/" + roomTypeId + "/rates")
                        .with(authentication(adminAuth())).with(csrf())
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rateCents").value(7500));
    }

    // ---- Auth helpers ----

    private Authentication adminAuth() {
        return authenticationFor(
                new CurrentUser(adminId, "prop-admin", "Property Admin", UserRoleType.ADMIN,
                        Set.of("ADMIN"), Set.of("property.read", "property.write",
                                "reservations.read", "reservations.write")),
                "ROLE_ADMIN", "property.read", "property.write",
                "reservations.read", "reservations.write"
        );
    }

    private Authentication noPermsAuth() {
        return authenticationFor(
                new CurrentUser(999L, "nobody", "Nobody", UserRoleType.STUDENT,
                        Set.of("STUDENT"), Set.of()),
                "ROLE_STUDENT"
        );
    }

    private static Authentication authenticationFor(CurrentUser cu, String... authorities) {
        var granted = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(cu, "n/a", granted);
    }
}
