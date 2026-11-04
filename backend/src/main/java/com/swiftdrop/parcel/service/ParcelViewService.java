package com.swiftdrop.parcel.service;

import static com.swiftdrop.parcel.repository.ParcelSpecifications.adminSearch;
import static com.swiftdrop.parcel.repository.ParcelSpecifications.any;
import static com.swiftdrop.parcel.repository.ParcelSpecifications.createdBetween;
import static com.swiftdrop.parcel.repository.ParcelSpecifications.customerSearch;
import static com.swiftdrop.parcel.repository.ParcelSpecifications.deliveredTo;
import static com.swiftdrop.parcel.repository.ParcelSpecifications.hasStatus;
import static com.swiftdrop.parcel.repository.ParcelSpecifications.idIn;
import static com.swiftdrop.parcel.repository.ParcelSpecifications.idNotIn;
import static com.swiftdrop.parcel.repository.ParcelSpecifications.none;
import static com.swiftdrop.parcel.repository.ParcelSpecifications.ownedByCustomer;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.model.MerchantApprovalStatus;
import com.swiftdrop.merchant.service.MerchantService;
import com.swiftdrop.parcel.dto.request.AdminParcelFilter;
import com.swiftdrop.parcel.dto.request.AdminParcelTab;
import com.swiftdrop.parcel.dto.request.CustomerParcelFilter;
import com.swiftdrop.parcel.dto.response.AdminParcelDetailResponse;
import com.swiftdrop.parcel.dto.response.AdminParcelRowResponse;
import com.swiftdrop.parcel.dto.response.CustomerParcelDetailResponse;
import com.swiftdrop.parcel.dto.response.CustomerParcelSummaryResponse;
import com.swiftdrop.parcel.mapper.ParcelViewMapper;
import com.swiftdrop.parcel.model.Parcel;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Reads for the customer and admin parcel screens (dashboard, My Deliveries, Delivery Details,
 * admin Parcels). Used only by this module's controllers; other modules use {@link ParcelService}.
 *
 * <p>Which CREATED parcels have a courier comes from the dispatch module through
 * {@link DispatchStateLookup}. Without that bean every CREATED parcel counts as UNASSIGNED and the
 * ASSIGNED tab is empty.
 */
@Service
@RequiredArgsConstructor
public class ParcelViewService {

    /** Date filters are whole days in Sri Lanka time. */
    static final ZoneId SRI_LANKA = ZoneId.of("Asia/Colombo");

    static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private static final List<String> ON_THE_WAY = List.of(ParcelStatusCode.PICKED_UP.name(),
            ParcelStatusCode.IN_TRANSIT.name(), ParcelStatusCode.OUT_FOR_DELIVERY.name());

    private final ParcelRepository parcelRepository;
    private final ParcelStatusHistoryRepository statusHistoryRepository;
    private final ParcelService parcelService;
    private final MerchantService merchantService;
    private final ObjectProvider<DispatchStateLookup> dispatchStateLookup;

    // ---- Customer ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<ParcelStatusCode, Long> customerCounts(UUID customerUserId) {
        return parcelService.countByStatusForCustomer(customerUserId);
    }

    /** The customer's own bookings, newest first. */
    @Transactional(readOnly = true)
    public Page<CustomerParcelSummaryResponse> listForCustomer(UUID customerUserId, CustomerParcelFilter filter,
                                                               int page, int size) {
        Specification<Parcel> spec = ownedByCustomer(customerUserId)
                .and(createdBetween(startOf(filter.bookedFrom()), endOf(filter.bookedTo())));
        if (StringUtils.hasText(filter.search())) {
            spec = spec.and(customerSearch(filter.search()));
        }
        if (filter.status() != null) {
            spec = spec.and(hasStatus(List.of(filter.status().name())));
        }
        return parcelRepository.findAll(spec, PageRequest.of(page, size, NEWEST_FIRST))
                .map(ParcelViewMapper::toCustomerSummary);
    }

    /**
     * One of the customer's own parcels with its history.
     *
     * @throws NotFoundException if there is no such parcel or it belongs to someone else (the
     *                           answer is the same, so other customers' tracking numbers can't be probed)
     */
    @Transactional(readOnly = true)
    public CustomerParcelDetailResponse getForCustomer(UUID customerUserId, String trackingNumber) {
        Parcel parcel = parcelRepository.findByTrackingNumber(ParcelService.normalizeTrackingNumber(trackingNumber))
                .filter(found -> customerUserId.equals(found.getCustomerUserId()))
                .orElseThrow(() -> new NotFoundException("Delivery not found"));
        return ParcelViewMapper.toCustomerDetail(parcel,
                statusHistoryRepository.findByParcelIdOrderByChangedAtAsc(parcel.getId()));
    }

    // ---- Admin -------------------------------------------------------------------------------

    /** Number of parcels in each admin tab, in tab order. */
    @Transactional(readOnly = true)
    public Map<AdminParcelTab, Long> adminCounts() {
        Map<ParcelStatusCode, Long> byStatus = parcelService.countByStatus();
        Set<UUID> assigned = assignedParcelIds();
        long created = byStatus.get(ParcelStatusCode.CREATED);
        long createdAndAssigned = assigned == null || assigned.isEmpty() ? 0
                : parcelRepository.count(hasStatus(List.of(ParcelStatusCode.CREATED.name())).and(idIn(assigned)));
        Map<AdminParcelTab, Long> counts = new EnumMap<>(AdminParcelTab.class);
        counts.put(AdminParcelTab.ALL, byStatus.values().stream().mapToLong(Long::longValue).sum());
        counts.put(AdminParcelTab.UNASSIGNED, created - createdAndAssigned);
        counts.put(AdminParcelTab.ASSIGNED, createdAndAssigned);
        counts.put(AdminParcelTab.IN_TRANSIT, byStatus.get(ParcelStatusCode.PICKED_UP)
                + byStatus.get(ParcelStatusCode.IN_TRANSIT) + byStatus.get(ParcelStatusCode.OUT_FOR_DELIVERY));
        counts.put(AdminParcelTab.DELIVERED, byStatus.get(ParcelStatusCode.DELIVERED));
        counts.put(AdminParcelTab.FAILED, byStatus.get(ParcelStatusCode.FAILED));
        return Collections.unmodifiableMap(counts);
    }

    /** All parcels matching the filters, newest first. */
    @Transactional(readOnly = true)
    public Page<AdminParcelRowResponse> listForAdmin(AdminParcelFilter filter, int page, int size) {
        Map<UUID, String> merchantNames = merchantNames();
        Set<UUID> assigned = assignedParcelIds();
        Specification<Parcel> spec = any()
                .and(inTab(filter.tab() == null ? AdminParcelTab.ALL : filter.tab(), assigned))
                .and(createdBetween(startOf(filter.createdFrom()), endOf(filter.createdTo())));
        if (StringUtils.hasText(filter.search())) {
            String term = filter.search().trim().toLowerCase(Locale.ROOT);
            List<UUID> matchingMerchants = merchantNames.entrySet().stream()
                    .filter(entry -> entry.getValue().toLowerCase(Locale.ROOT).contains(term))
                    .map(Map.Entry::getKey)
                    .toList();
            spec = spec.and(adminSearch(filter.search(), matchingMerchants));
        }
        if (filter.status() != null) {
            spec = spec.and(hasStatus(List.of(filter.status().name())));
        }
        if (StringUtils.hasText(filter.area())) {
            spec = spec.and(deliveredTo(filter.area()));
        }
        return parcelRepository.findAll(spec, PageRequest.of(page, size, NEWEST_FIRST))
                .map(parcel -> ParcelViewMapper.toAdminRow(parcel, merchantNames.get(parcel.getMerchantId()),
                        dispatchState(parcel, assigned)));
    }

    /** @throws NotFoundException if there is no parcel with this id */
    @Transactional(readOnly = true)
    public AdminParcelDetailResponse getForAdmin(UUID parcelId) {
        Parcel parcel = parcelRepository.findWithDetailsById(parcelId)
                .orElseThrow(() -> new NotFoundException("Parcel not found"));
        String merchantName = parcel.getMerchantId() == null ? null : merchantNames().get(parcel.getMerchantId());
        return ParcelViewMapper.toAdminDetail(parcel, merchantName, dispatchState(parcel, assignedParcelIds()),
                statusHistoryRepository.findByParcelIdOrderByChangedAtAsc(parcelId));
    }

    /** Parcels with an open courier assignment, or {@code null} while the dispatch module can't tell. */
    private Set<UUID> assignedParcelIds() {
        DispatchStateLookup lookup = dispatchStateLookup.getIfAvailable();
        return lookup == null ? null : lookup.parcelsWithOpenAssignment();
    }

    /** UNASSIGNED / ASSIGNED for a CREATED parcel when known; {@code null} otherwise. */
    private static String dispatchState(Parcel parcel, Set<UUID> assigned) {
        if (assigned == null || !ParcelStatusCode.CREATED.name().equals(parcel.getCurrentStatus().getStatusCode())) {
            return null;
        }
        return assigned.contains(parcel.getId()) ? AdminParcelTab.ASSIGNED.name() : AdminParcelTab.UNASSIGNED.name();
    }

    /** @param assigned parcels with a courier, or {@code null} when unknown (all CREATED are unassigned) */
    private static Specification<Parcel> inTab(AdminParcelTab tab, Set<UUID> assigned) {
        Specification<Parcel> created = hasStatus(List.of(ParcelStatusCode.CREATED.name()));
        return switch (tab) {
            case ALL -> any();
            case UNASSIGNED -> assigned == null ? created : created.and(idNotIn(assigned));
            case ASSIGNED -> assigned == null ? none() : created.and(idIn(assigned));
            case IN_TRANSIT -> hasStatus(ON_THE_WAY);
            case DELIVERED -> hasStatus(List.of(ParcelStatusCode.DELIVERED.name()));
            case FAILED -> hasStatus(List.of(ParcelStatusCode.FAILED.name()));
        };
    }

    /**
     * Business names of approved merchants (only they can create orders), through the merchant
     * module's public service.
     */
    private Map<UUID, String> merchantNames() {
        return merchantService.listByStatus(MerchantApprovalStatus.APPROVED).stream()
                .collect(Collectors.toMap(MerchantResponse::merchantId, MerchantResponse::businessName,
                        (first, second) -> first));
    }

    private static Instant startOf(LocalDate day) {
        return day == null ? null : day.atStartOfDay(SRI_LANKA).toInstant();
    }

    /** Start of the next day, so the whole of {@code day} is included. */
    private static Instant endOf(LocalDate day) {
        return day == null ? null : day.plusDays(1).atStartOfDay(SRI_LANKA).toInstant();
    }
}
