package com.swiftdrop.dispatch.service;

import static com.swiftdrop.dispatch.repository.CourierSpecifications.any;
import static com.swiftdrop.dispatch.repository.CourierSpecifications.coversZone;
import static com.swiftdrop.dispatch.repository.CourierSpecifications.hasAvailability;
import static com.swiftdrop.dispatch.repository.CourierSpecifications.hasVehicleType;
import static com.swiftdrop.dispatch.repository.CourierSpecifications.isActive;
import static com.swiftdrop.dispatch.repository.CourierSpecifications.matchesText;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.config.api.ZoneApi;
import com.swiftdrop.dispatch.dto.request.CourierFilter;
import com.swiftdrop.dispatch.dto.request.CreateCourierRequest;
import com.swiftdrop.dispatch.dto.request.UpdateCourierRequest;
import com.swiftdrop.dispatch.dto.response.CourierResponse;
import com.swiftdrop.dispatch.dto.response.CourierSummaryResponse;
import com.swiftdrop.dispatch.dto.response.CourierWorkload;
import com.swiftdrop.dispatch.mapper.CourierMapper;
import com.swiftdrop.dispatch.model.Courier;
import com.swiftdrop.dispatch.model.CourierAvailability;
import com.swiftdrop.dispatch.repository.AvailabilityCount;
import com.swiftdrop.dispatch.repository.CourierRepository;
import com.swiftdrop.dispatch.repository.DeliveryAssignmentRepository;
import com.swiftdrop.dispatch.repository.WorkloadCount;
import com.swiftdrop.user.api.UserApi;
import com.swiftdrop.user.api.UserSearch;
import com.swiftdrop.user.api.UserSummary;
import com.swiftdrop.user.dto.NewUser;
import com.swiftdrop.user.model.AccountStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Couriers for the dispatcher: add a courier, find couriers, see their workload, change their
 * availability, vehicle and service areas. A courier is a COURIER account (user module, created
 * through {@link UserApi}) plus a profile row owned by this module.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourierService {

    /** "Completed today" and the other daily counts are whole days in Sri Lanka time. */
    static final ZoneId SRI_LANKA = ZoneId.of("Asia/Colombo");

    /** Most accounts the name/email/phone search takes from the user module (UserApi can't be asked for "all"). */
    static final int MAX_SEARCH_MATCHES = 500;

    static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));

    static final String UNKNOWN_SERVICE_AREA = "Unknown or inactive service area";

    private static final String NOT_SAVED =
            "The courier could not be saved: the vehicle registration is already in use";

    private final CourierRepository courierRepository;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final UserApi userApi;
    private final PasswordEncoder passwordEncoder;
    private final ZoneApi zoneApi;

    // ---- Add and change ----------------------------------------------------------------------

    /**
     * Creates the courier's account (active, email already verified, so they can sign in at once)
     * and profile in one transaction. The new courier starts OFFLINE.
     *
     * @throws ConflictException   if the email or the vehicle registration is already used
     * @throws BadRequestException if a service area is unknown or inactive
     */
    @Transactional
    public CourierResponse create(CreateCourierRequest request) {
        String email = request.email().trim();
        if (userApi.emailExists(email)) {
            throw ConflictException.forField("email", "An account with this email already exists");
        }
        String registration = normalizeRegistration(request.vehicleRegistration());
        if (courierRepository.existsByVehicleRegistration(registration)) {
            throw ConflictException.forField("vehicleRegistration", "A courier with this vehicle registration already exists");
        }
        requireActiveZones(request.serviceAreaIds());

        UUID userId = userApi.createUser(NewUser.withPassword(request.fullName(), email, request.phone(),
                passwordEncoder.encode(request.password()), Role.COURIER, AccountStatus.ACTIVE).withVerifiedEmail()).id();

        Courier courier = new Courier();
        courier.setUserId(userId);
        courier.setVehicleType(request.vehicleType().trim());
        courier.setVehicleRegistration(registration);
        courier.setAvailabilityStatus(CourierAvailability.OFFLINE);
        if (request.serviceAreaIds() != null) {
            courier.getServiceAreaIds().addAll(request.serviceAreaIds());
        }
        Courier saved = save(courier);
        log.info("Courier {} added", saved.getId());
        return CourierMapper.toResponse(saved, userOf(saved), CourierWorkload.EMPTY);
    }

    /**
     * Changes the vehicle and/or the service areas; fields left {@code null} stay as they are.
     *
     * @throws BadRequestException if nothing is given
     * @throws NotFoundException   if there is no such courier
     * @throws BadRequestException also if a service area is unknown or inactive
     * @throws ConflictException   if the registration belongs to another courier
     */
    @Transactional
    public CourierResponse update(UUID courierId, UpdateCourierRequest request) {
        if (request.vehicleType() == null && request.vehicleRegistration() == null
                && request.serviceAreaIds() == null) {
            throw new BadRequestException("Nothing to update");
        }
        Courier courier = find(courierId);
        if (request.vehicleType() != null) {
            courier.setVehicleType(request.vehicleType().trim());
        }
        if (request.vehicleRegistration() != null) {
            String registration = normalizeRegistration(request.vehicleRegistration());
            if (!registration.equals(courier.getVehicleRegistration())) {
                if (courierRepository.existsByVehicleRegistrationAndIdNot(registration, courierId)) {
                    throw ConflictException.forField("vehicleRegistration",
                            "Another courier already has this vehicle registration");
                }
                courier.setVehicleRegistration(registration);
            }
        }
        if (request.serviceAreaIds() != null) {
            requireActiveZones(request.serviceAreaIds());
            courier.getServiceAreaIds().clear();
            courier.getServiceAreaIds().addAll(request.serviceAreaIds());
        }
        Courier saved = save(courier);
        return CourierMapper.toResponse(saved, userOf(saved), workloads(Set.of(courierId)).getOrDefault(courierId,
                CourierWorkload.EMPTY));
    }

    /**
     * Sets Online / On Break / Offline. A courier who is deactivated, or whose account can't sign
     * in, can't go online. Going offline or on break keeps their open assignments: the dispatcher
     * reassigns them.
     *
     * @throws NotFoundException if there is no such courier
     * @throws ConflictException if the courier can't go online
     */
    @Transactional
    public CourierResponse changeAvailability(UUID courierId, CourierAvailability availability) {
        Courier courier = find(courierId);
        UserSummary user = userOf(courier);
        if (availability == CourierAvailability.ONLINE
                && (!courier.isActive() || user.accountStatus() != AccountStatus.ACTIVE)) {
            throw new ConflictException("This courier is deactivated and cannot go online");
        }
        courier.setAvailabilityStatus(availability);
        return CourierMapper.toResponse(courier, user,
                workloads(Set.of(courierId)).getOrDefault(courierId, CourierWorkload.EMPTY));
    }

    // ---- Read --------------------------------------------------------------------------------

    /** The couriers matching the filters, newest first, each with today's workload. */
    @Transactional(readOnly = true)
    public Page<CourierResponse> list(CourierFilter filter, int page, int size) {
        Specification<Courier> spec = any();
        if (StringUtils.hasText(filter.search())) {
            String text = filter.search().trim();
            // Names, emails and phone numbers live in the user module: ask it which couriers' accounts match.
            Set<UUID> matchingUsers = userApi.listUsers(new UserSearch(text, Role.COURIER, null), PageRequest.of(0, MAX_SEARCH_MATCHES))
                    .stream().map(UserSummary::id).collect(Collectors.toSet());
            spec = spec.and(matchesText(matchingUsers, text));
        }
        if (filter.availability() != null) {
            spec = spec.and(hasAvailability(filter.availability()));
        }
        if (filter.serviceAreaId() != null) {
            spec = spec.and(coversZone(filter.serviceAreaId()));
        }
        if (StringUtils.hasText(filter.vehicleType())) {
            spec = spec.and(hasVehicleType(filter.vehicleType()));
        }
        if (filter.active() != null) {
            spec = spec.and(isActive(filter.active()));
        }

        Page<Courier> found = courierRepository.findAll(spec, PageRequest.of(page, size, NEWEST_FIRST));
        Map<UUID, CourierWorkload> workloads = workloads(found.getContent().stream().map(Courier::getId).toList());
        return found.map(courier -> CourierMapper.toResponse(courier, userOf(courier),
                workloads.getOrDefault(courier.getId(), CourierWorkload.EMPTY)));
    }

    /** @throws NotFoundException if there is no such courier */
    @Transactional(readOnly = true)
    public CourierResponse get(UUID courierId) {
        Courier courier = find(courierId);
        return CourierMapper.toResponse(courier, userOf(courier),
                workloads(Set.of(courierId)).getOrDefault(courierId, CourierWorkload.EMPTY));
    }

    /** Active couriers per availability, for the cards above the list. */
    @Transactional(readOnly = true)
    public CourierSummaryResponse summary() {
        Map<CourierAvailability, Long> counts = new EnumMap<>(CourierAvailability.class);
        for (AvailabilityCount count : courierRepository.countActiveByAvailability()) {
            counts.merge(count.availability(), count.total(), Long::sum);
        }
        long online = counts.getOrDefault(CourierAvailability.ONLINE, 0L);
        long onBreak = counts.getOrDefault(CourierAvailability.ON_BREAK, 0L);
        long offline = counts.getOrDefault(CourierAvailability.OFFLINE, 0L);
        return new CourierSummaryResponse(online + onBreak + offline, online, onBreak, offline);
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private Courier find(UUID courierId) {
        return courierRepository.findById(courierId).orElseThrow(() -> new NotFoundException("Courier not found"));
    }

    /** The courier's account. Every courier has one (foreign key), so a miss is a programming or data error. */
    private UserSummary userOf(Courier courier) {
        return userApi.findUser(courier.getUserId())
                .orElseThrow(() -> new IllegalStateException("Courier " + courier.getId() + " has no user account"));
    }

    private Courier save(Courier courier) {
        try {
            return courierRepository.saveAndFlush(courier);
        } catch (DataIntegrityViolationException ex) {
            // A lost race on the registration, or a zone id that is not in delivery_zones.
            throw new ConflictException(NOT_SAVED);
        }
    }

    /**
     * Service areas must be active delivery zones (config module). The whole set is checked, so a
     * zone deactivated since it was given to the courier must be left out when the set is replaced.
     *
     * @throws BadRequestException if one isn't
     */
    private void requireActiveZones(Collection<UUID> serviceAreaIds) {
        if (serviceAreaIds != null && !serviceAreaIds.isEmpty() && !zoneApi.missingOrInactive(serviceAreaIds).isEmpty()) {
            throw new BadRequestException(UNKNOWN_SERVICE_AREA);
        }
    }

    /** Upper case with single spaces, so "wp cab  1234" and "WP CAB 1234" are the same vehicle. */
    static String normalizeRegistration(String registration) {
        String normalized = registration.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new BadRequestException("Vehicle registration is required");
        }
        return normalized;
    }

    /** Today's workload per courier; couriers with no assignments are missing from the map. */
    private Map<UUID, CourierWorkload> workloads(Collection<UUID> courierIds) {
        if (courierIds.isEmpty()) {
            return Map.of();
        }
        Instant startOfDay = LocalDate.now(SRI_LANKA).atStartOfDay(SRI_LANKA).toInstant();
        Map<UUID, int[]> counts = new HashMap<>(); // per courier: assigned, in progress, completed today
        for (WorkloadCount row : assignmentRepository.countWorkload(courierIds, startOfDay)) {
            int[] count = counts.computeIfAbsent(row.courierId(), id -> new int[3]);
            switch (row.status()) {
                case ASSIGNED -> count[0] += (int) row.total();
                case IN_PROGRESS -> count[1] += (int) row.total();
                case COMPLETED -> count[2] += (int) row.total();
                default -> { }
            }
        }
        Map<UUID, CourierWorkload> workloads = new HashMap<>();
        counts.forEach((id, count) -> workloads.put(id, CourierWorkload.of(count[0], count[1], count[2])));
        return workloads;
    }
}
