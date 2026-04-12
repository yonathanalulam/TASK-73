package com.dojostay.property;

import com.dojostay.audit.AuditAction;
import com.dojostay.audit.AuditService;
import com.dojostay.auth.CurrentUser;
import com.dojostay.auth.CurrentUserResolver;
import com.dojostay.common.exception.BusinessException;
import com.dojostay.common.exception.NotFoundException;
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
import com.dojostay.scopes.DataScopeService;
import com.dojostay.scopes.EffectiveScope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Property + lodging reservations with scope-aware reads.
 *
 * <p>Availability is computed by listing all active beds under a property and
 * subtracting the bed ids that have any non-cancelled reservation overlapping
 * the requested window. The overlap predicate lives in the repository query to
 * avoid loading the full reservation table.
 *
 * <p>Phase B7 extends this service with:
 * <ul>
 *   <li>amenity CRUD (list/upsert/remove, unique on {@code (propertyId, code)})</li>
 *   <li>image upload/list/delete (mirrors the credential file pipeline)</li>
 *   <li>room-type CRUD used by the comparison view</li>
 *   <li>nightly-rate calendar upsert + range read</li>
 * </ul>
 * These extensions intentionally reuse the existing scope check
 * ({@link #assertOrgAccessible(Long)}) so every new operation goes through the
 * same existence-hiding path as the original endpoints.
 */
@Service
public class PropertyService {

    private static final DateTimeFormatter FILE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final LodgingReservationRepository reservationRepository;
    private final PropertyAmenityRepository amenityRepository;
    private final PropertyImageRepository imageRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final NightlyRateRepository nightlyRateRepository;
    private final DataScopeService dataScopeService;
    private final AuditService auditService;
    private final PropertyUploadProperties uploadProperties;

    public PropertyService(PropertyRepository propertyRepository,
                           RoomRepository roomRepository,
                           BedRepository bedRepository,
                           LodgingReservationRepository reservationRepository,
                           PropertyAmenityRepository amenityRepository,
                           PropertyImageRepository imageRepository,
                           RoomTypeRepository roomTypeRepository,
                           NightlyRateRepository nightlyRateRepository,
                           DataScopeService dataScopeService,
                           AuditService auditService,
                           PropertyUploadProperties uploadProperties) {
        this.propertyRepository = propertyRepository;
        this.roomRepository = roomRepository;
        this.bedRepository = bedRepository;
        this.reservationRepository = reservationRepository;
        this.amenityRepository = amenityRepository;
        this.imageRepository = imageRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.nightlyRateRepository = nightlyRateRepository;
        this.dataScopeService = dataScopeService;
        this.auditService = auditService;
        this.uploadProperties = uploadProperties;
    }

    @Transactional(readOnly = true)
    public List<PropertyResponse> list() {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        List<Property> all = propertyRepository.findAll();
        return all.stream()
                .filter(p -> scope.fullAccess() || scope.hasOrganization(p.getOrganizationId()))
                .map(PropertyService::toPropertyResponse)
                .toList();
    }

    @Transactional
    public PropertyResponse create(CreatePropertyRequest req, String sourceIp) {
        assertOrgAccessible(req.organizationId());
        Property p = new Property();
        p.setOrganizationId(req.organizationId());
        p.setCode(req.code());
        p.setName(req.name());
        p.setAddress(req.address());
        p.setDescription(req.description());
        p.setPolicies(req.policies());
        p.setActive(true);
        Property saved = propertyRepository.save(p);
        auditService.record(AuditAction.PROPERTY_CREATED, actorId(), actorUsername(),
                "PROPERTY", String.valueOf(saved.getId()),
                "Property created: " + saved.getCode(), sourceIp);
        return toPropertyResponse(saved);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(Long propertyId, LocalDate startsOn, LocalDate endsOn) {
        if (startsOn == null || endsOn == null || !endsOn.isAfter(startsOn)) {
            throw new BusinessException("INVALID_RANGE",
                    "endsOn must be strictly after startsOn", HttpStatus.BAD_REQUEST);
        }
        Property p = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new NotFoundException("Property not found"));
        assertOrgAccessible(p.getOrganizationId());

        List<Room> rooms = roomRepository.findByPropertyId(propertyId);
        Map<Long, Room> roomsById = rooms.stream()
                .collect(Collectors.toMap(Room::getId, r -> r));

        List<Bed> beds = new ArrayList<>();
        for (Room r : rooms) {
            if (r.isActive()) beds.addAll(bedRepository.findByRoomId(r.getId()));
        }
        beds.removeIf(b -> !b.isActive());

        Set<Long> bedIds = beds.stream().map(Bed::getId).collect(Collectors.toCollection(HashSet::new));
        Set<Long> booked = bedIds.isEmpty()
                ? Set.of()
                : new HashSet<>(reservationRepository.findBookedBedIdsInRange(bedIds, startsOn, endsOn));

        List<AvailabilityResponse.BedAvailability> out = new ArrayList<>();
        for (Bed b : beds) {
            Room room = roomsById.get(b.getRoomId());
            out.add(new AvailabilityResponse.BedAvailability(
                    b.getId(), b.getLabel(),
                    b.getRoomId(), room != null ? room.getName() : null,
                    !booked.contains(b.getId())
            ));
        }
        return new AvailabilityResponse(propertyId, startsOn, endsOn, out);
    }

    @Transactional
    public ReservationResponse reserve(CreateReservationRequest req, String sourceIp) {
        if (!req.endsOn().isAfter(req.startsOn())) {
            throw new BusinessException("INVALID_RANGE",
                    "endsOn must be strictly after startsOn", HttpStatus.BAD_REQUEST);
        }
        Bed bed = bedRepository.findById(req.bedId())
                .orElseThrow(() -> new NotFoundException("Bed not found"));
        Room room = roomRepository.findById(bed.getRoomId())
                .orElseThrow(() -> new NotFoundException("Room not found"));
        Property property = propertyRepository.findById(room.getPropertyId())
                .orElseThrow(() -> new NotFoundException("Property not found"));
        assertOrgAccessible(property.getOrganizationId());

        List<Long> conflicts = reservationRepository.findBookedBedIdsInRange(
                Set.of(bed.getId()), req.startsOn(), req.endsOn());
        if (!conflicts.isEmpty()) {
            throw new BusinessException("BED_UNAVAILABLE",
                    "Bed is already reserved in that date range",
                    HttpStatus.CONFLICT);
        }

        LodgingReservation r = new LodgingReservation();
        r.setOrganizationId(property.getOrganizationId());
        r.setBedId(bed.getId());
        r.setStudentId(req.studentId());
        r.setGuestName(req.guestName());
        r.setStartsOn(req.startsOn());
        r.setEndsOn(req.endsOn());
        r.setNotes(req.notes());
        r.setStatus(LodgingReservation.Status.BOOKED);
        r.setCreatedByUserId(actorId() != null ? actorId() : 0L);
        LodgingReservation saved = reservationRepository.save(r);

        auditService.record(AuditAction.RESERVATION_CREATED, actorId(), actorUsername(),
                "LODGING_RESERVATION", String.valueOf(saved.getId()),
                "Reserved bed " + bed.getId() + " for " + req.guestName(), sourceIp);
        return toReservationResponse(saved);
    }

    @Transactional
    public ReservationResponse cancel(Long reservationId, String sourceIp) {
        LodgingReservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found"));
        assertOrgAccessible(r.getOrganizationId());
        if (r.getStatus() == LodgingReservation.Status.CANCELLED) {
            return toReservationResponse(r);
        }
        r.setStatus(LodgingReservation.Status.CANCELLED);
        r.setCancelledAt(Instant.now());
        LodgingReservation saved = reservationRepository.save(r);
        auditService.record(AuditAction.RESERVATION_CANCELLED, actorId(), actorUsername(),
                "LODGING_RESERVATION", String.valueOf(saved.getId()),
                "Reservation cancelled", sourceIp);
        return toReservationResponse(saved);
    }

    // ---------------------------------------------------------------------
    // Phase B7 — amenities
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PropertyAmenityResponse> listAmenities(Long propertyId) {
        Property p = loadAccessibleProperty(propertyId);
        return amenityRepository.findByPropertyIdOrderByCodeAsc(p.getId()).stream()
                .map(PropertyService::toAmenityResponse)
                .toList();
    }

    @Transactional
    public PropertyAmenityResponse upsertAmenity(Long propertyId, PropertyAmenityRequest req, String sourceIp) {
        Property p = loadAccessibleProperty(propertyId);
        PropertyAmenity amenity = amenityRepository.findByPropertyIdOrderByCodeAsc(p.getId()).stream()
                .filter(a -> a.getCode().equalsIgnoreCase(req.code()))
                .findFirst()
                .orElseGet(PropertyAmenity::new);
        amenity.setPropertyId(p.getId());
        amenity.setCode(req.code());
        amenity.setLabel(req.label());
        amenity.setIcon(req.icon());
        PropertyAmenity saved = amenityRepository.save(amenity);
        auditService.record(AuditAction.PROPERTY_AMENITY_UPSERTED, actorId(), actorUsername(),
                "PROPERTY_AMENITY", String.valueOf(saved.getId()),
                "Amenity " + req.code() + " upserted on property " + p.getId(), sourceIp);
        return toAmenityResponse(saved);
    }

    @Transactional
    public void removeAmenity(Long propertyId, String code, String sourceIp) {
        Property p = loadAccessibleProperty(propertyId);
        amenityRepository.deleteByPropertyIdAndCode(p.getId(), code);
        auditService.record(AuditAction.PROPERTY_AMENITY_REMOVED, actorId(), actorUsername(),
                "PROPERTY_AMENITY", p.getId() + ":" + code,
                "Amenity " + code + " removed from property " + p.getId(), sourceIp);
    }

    // ---------------------------------------------------------------------
    // Phase B7 — images
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PropertyImageResponse> listImages(Long propertyId) {
        Property p = loadAccessibleProperty(propertyId);
        return imageRepository.findByPropertyIdOrderByDisplayOrderAsc(p.getId()).stream()
                .map(PropertyService::toImageResponse)
                .toList();
    }

    @Transactional
    public PropertyImageResponse uploadImage(Long propertyId, MultipartFile file,
                                             String caption, Integer displayOrder, String sourceIp) {
        Property p = loadAccessibleProperty(propertyId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("IMAGE_REQUIRED",
                    "An image file is required",
                    HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > uploadProperties.getMaxFileSize()) {
            throw new BusinessException("FILE_TOO_LARGE",
                    "Image exceeds the " + uploadProperties.getMaxFileSize() + " byte limit",
                    HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String mime = file.getContentType() == null
                ? "application/octet-stream"
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (!uploadProperties.getAllowedMimeTypes().contains(mime)) {
            throw new BusinessException("FILE_TYPE_REJECTED",
                    "File type " + mime + " is not accepted for property images",
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }

        String storagePath;
        try {
            Path root = Paths.get(uploadProperties.getUploadDir(),
                    String.valueOf(p.getOrganizationId()),
                    String.valueOf(p.getId()));
            Files.createDirectories(root);
            String ext = extensionFor(mime);
            String fname = p.getId() + "-" + FILE_STAMP.format(Instant.now()) + "-"
                    + UUID.randomUUID() + ext;
            Path target = root.resolve(fname);
            Files.copy(new ByteArrayInputStream(file.getBytes()), target,
                    StandardCopyOption.REPLACE_EXISTING);
            storagePath = target.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new BusinessException("FILE_STORE_FAILED",
                    "Could not store uploaded image",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        PropertyImage img = new PropertyImage();
        img.setPropertyId(p.getId());
        img.setStoragePath(storagePath);
        img.setCaption(caption);
        img.setDisplayOrder(displayOrder == null ? 0 : displayOrder);
        PropertyImage saved = imageRepository.save(img);
        auditService.record(AuditAction.PROPERTY_IMAGE_UPLOADED, actorId(), actorUsername(),
                "PROPERTY_IMAGE", String.valueOf(saved.getId()),
                "Image uploaded for property " + p.getId() + " mime=" + mime
                        + " size=" + file.getSize(),
                sourceIp);
        return toImageResponse(saved);
    }

    @Transactional
    public void deleteImage(Long imageId, String sourceIp) {
        PropertyImage img = imageRepository.findById(imageId)
                .orElseThrow(() -> new NotFoundException("Property image not found"));
        Property p = loadAccessibleProperty(img.getPropertyId());
        imageRepository.delete(img);
        auditService.record(AuditAction.PROPERTY_IMAGE_DELETED, actorId(), actorUsername(),
                "PROPERTY_IMAGE", String.valueOf(imageId),
                "Image " + imageId + " deleted from property " + p.getId(), sourceIp);
    }

    // ---------------------------------------------------------------------
    // Phase B7 — room types
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RoomTypeResponse> listRoomTypes(Long propertyId) {
        Property p = loadAccessibleProperty(propertyId);
        return roomTypeRepository.findByPropertyIdOrderByCodeAsc(p.getId()).stream()
                .map(PropertyService::toRoomTypeResponse)
                .toList();
    }

    @Transactional
    public RoomTypeResponse createRoomType(Long propertyId, RoomTypeRequest req, String sourceIp) {
        Property p = loadAccessibleProperty(propertyId);
        Optional<RoomType> existing = roomTypeRepository.findByPropertyIdAndCode(p.getId(), req.code());
        if (existing.isPresent()) {
            throw new BusinessException("DUPLICATE_ROOM_TYPE",
                    "A room type with code " + req.code() + " already exists for this property",
                    HttpStatus.CONFLICT);
        }
        RoomType rt = new RoomType();
        rt.setPropertyId(p.getId());
        applyRoomType(rt, req);
        RoomType saved = roomTypeRepository.save(rt);
        auditService.record(AuditAction.ROOM_TYPE_CREATED, actorId(), actorUsername(),
                "ROOM_TYPE", String.valueOf(saved.getId()),
                "Room type " + req.code() + " created on property " + p.getId(), sourceIp);
        return toRoomTypeResponse(saved);
    }

    @Transactional
    public RoomTypeResponse updateRoomType(Long roomTypeId, RoomTypeRequest req, String sourceIp) {
        RoomType rt = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new NotFoundException("Room type not found"));
        Property p = loadAccessibleProperty(rt.getPropertyId());
        if (!rt.getCode().equalsIgnoreCase(req.code())) {
            Optional<RoomType> conflict = roomTypeRepository.findByPropertyIdAndCode(p.getId(), req.code());
            if (conflict.isPresent() && !conflict.get().getId().equals(rt.getId())) {
                throw new BusinessException("DUPLICATE_ROOM_TYPE",
                        "A room type with code " + req.code() + " already exists for this property",
                        HttpStatus.CONFLICT);
            }
        }
        applyRoomType(rt, req);
        RoomType saved = roomTypeRepository.save(rt);
        auditService.record(AuditAction.ROOM_TYPE_UPDATED, actorId(), actorUsername(),
                "ROOM_TYPE", String.valueOf(saved.getId()),
                "Room type " + saved.getCode() + " updated on property " + p.getId(), sourceIp);
        return toRoomTypeResponse(saved);
    }

    // ---------------------------------------------------------------------
    // Phase B7 — nightly rate calendar
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<NightlyRateResponse> listNightlyRates(Long roomTypeId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new BusinessException("INVALID_RANGE",
                    "to must be on or after from", HttpStatus.BAD_REQUEST);
        }
        RoomType rt = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new NotFoundException("Room type not found"));
        loadAccessibleProperty(rt.getPropertyId());
        return nightlyRateRepository
                .findByRoomTypeIdAndStayDateBetweenOrderByStayDateAsc(rt.getId(), from, to)
                .stream()
                .map(PropertyService::toNightlyRateResponse)
                .toList();
    }

    @Transactional
    public NightlyRateResponse upsertNightlyRate(Long roomTypeId, NightlyRateRequest req, String sourceIp) {
        RoomType rt = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new NotFoundException("Room type not found"));
        Property p = loadAccessibleProperty(rt.getPropertyId());
        NightlyRate rate = nightlyRateRepository
                .findByRoomTypeIdAndStayDate(rt.getId(), req.stayDate())
                .orElseGet(NightlyRate::new);
        rate.setRoomTypeId(rt.getId());
        rate.setStayDate(req.stayDate());
        rate.setRateCents(req.rateCents());
        rate.setAvailableCount(req.availableCount());
        NightlyRate saved = nightlyRateRepository.save(rate);
        auditService.record(AuditAction.NIGHTLY_RATE_UPSERTED, actorId(), actorUsername(),
                "NIGHTLY_RATE", String.valueOf(saved.getId()),
                "Nightly rate upserted for room type " + rt.getId()
                        + " on " + req.stayDate() + " (property " + p.getId() + ")",
                sourceIp);
        return toNightlyRateResponse(saved);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private Property loadAccessibleProperty(Long propertyId) {
        Property p = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new NotFoundException("Property not found"));
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (!scope.fullAccess() && !scope.hasOrganization(p.getOrganizationId())) {
            // existence-hidden: out-of-scope reads and unknown ids look the same
            throw new NotFoundException("Property not found");
        }
        return p;
    }

    private void assertOrgAccessible(Long organizationId) {
        EffectiveScope scope = dataScopeService.forCurrentUser();
        if (scope.fullAccess()) return;
        if (organizationId == null || !scope.hasOrganization(organizationId)) {
            throw new BusinessException("OUT_OF_SCOPE",
                    "Target organization is not in your data scope",
                    HttpStatus.FORBIDDEN);
        }
    }

    private static void applyRoomType(RoomType rt, RoomTypeRequest req) {
        rt.setCode(req.code());
        rt.setName(req.name());
        rt.setDescription(req.description());
        rt.setMaxOccupancy(req.maxOccupancy());
        rt.setBaseRateCents(req.baseRateCents());
        rt.setFeatures(req.features());
    }

    private static String extensionFor(String mime) {
        return switch (mime) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> ".bin";
        };
    }

    private static Long actorId() {
        return CurrentUserResolver.current().map(CurrentUser::id).orElse(null);
    }

    private static String actorUsername() {
        return CurrentUserResolver.current().map(CurrentUser::username).orElse("system");
    }

    private static PropertyResponse toPropertyResponse(Property p) {
        return new PropertyResponse(
                p.getId(), p.getOrganizationId(), p.getCode(), p.getName(),
                p.getAddress(), p.getDescription(), p.getPolicies(),
                p.isActive(), p.getCreatedAt()
        );
    }

    private static ReservationResponse toReservationResponse(LodgingReservation r) {
        return new ReservationResponse(
                r.getId(), r.getOrganizationId(), r.getBedId(), r.getStudentId(),
                r.getGuestName(), r.getStartsOn(), r.getEndsOn(), r.getStatus(),
                r.getNotes(), r.getCreatedAt(), r.getCancelledAt()
        );
    }

    private static PropertyAmenityResponse toAmenityResponse(PropertyAmenity a) {
        return new PropertyAmenityResponse(
                a.getId(), a.getPropertyId(), a.getCode(), a.getLabel(), a.getIcon()
        );
    }

    private static PropertyImageResponse toImageResponse(PropertyImage i) {
        return new PropertyImageResponse(
                i.getId(), i.getPropertyId(), i.getStoragePath(),
                i.getCaption(), i.getDisplayOrder()
        );
    }

    private static RoomTypeResponse toRoomTypeResponse(RoomType r) {
        return new RoomTypeResponse(
                r.getId(), r.getPropertyId(), r.getCode(), r.getName(),
                r.getDescription(), r.getMaxOccupancy(), r.getBaseRateCents(),
                r.getFeatures()
        );
    }

    private static NightlyRateResponse toNightlyRateResponse(NightlyRate n) {
        return new NightlyRateResponse(
                n.getId(), n.getRoomTypeId(), n.getStayDate(),
                n.getRateCents(), n.getAvailableCount()
        );
    }
}
