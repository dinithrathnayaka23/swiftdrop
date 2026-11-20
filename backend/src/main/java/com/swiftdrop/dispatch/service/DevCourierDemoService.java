package com.swiftdrop.dispatch.service;

import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.dispatch.api.DispatchApi;
import com.swiftdrop.dispatch.dto.response.DevSampleDeliveryResponse;
import com.swiftdrop.dispatch.model.CourierAvailability;
import com.swiftdrop.parcel.dto.NewAddress;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.service.ParcelService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev profile only (Member 7, courier app): gives the signed-in courier a sample delivery, so the
 * courier screens can be tried without an admin assigning parcels by hand. Like
 * {@code DevTrackingDemoService}, it goes through the parcel module's {@link ParcelService}; the
 * assignment goes through dispatch's own {@link AssignmentService}, so its rules still apply.
 * Never registered outside the dev profile.
 */
@Slf4j
@Profile("dev")
@Service
@RequiredArgsConstructor
public class DevCourierDemoService {

    static final String NO_PROFILE = "This account has no courier profile";

    /** Where the samples are delivered to; one is picked at random so the list looks varied. */
    static final List<NewAddress> DROPOFFS = List.of(
            new NewAddress("Kasun Perera", "+94771234567", "12 Galle Road", null, "Colombo 03", "Colombo", "00300",
                    null),
            new NewAddress("Nadeesha Fernando", "+94712223344", "8 Lake Drive", null, "Rajagiriya", "Colombo",
                    "10100", null),
            new NewAddress("Mohamed Rizwan", "+94756667788", "21 Station Road", null, "Dehiwala", "Colombo", "10350",
                    null),
            new NewAddress("Ishara Jayasinghe", "+94701112233", "3 Temple Lane", null, "Nugegoda", "Colombo",
                    "10250", null));

    static final NewAddress PICKUP = new NewAddress("SwiftDrop Hub", "+94112345678", "45 Baseline Road", null,
            "Colombo 09", "Colombo", "00900", null);

    private final DispatchApi dispatchApi;
    private final CourierService courierService;
    private final AssignmentService assignmentService;
    private final ParcelService parcelService;

    /**
     * Books a sample parcel (as the courier, so they can also open its proof photo as its "customer")
     * and assigns it to them, setting them Online first if needed.
     *
     * @throws NotFoundException if the account has no courier profile
     * @throws ConflictException if the courier is deactivated, or their service areas don't cover
     *                           the sample's pickup area
     */
    @Transactional
    public DevSampleDeliveryResponse createSampleDelivery(UUID courierUserId) {
        UUID courierId = dispatchApi.findCourierId(courierUserId)
                .orElseThrow(() -> new NotFoundException(NO_PROFILE));
        if (courierService.get(courierId).availability() != CourierAvailability.ONLINE) {
            courierService.changeAvailability(courierId, CourierAvailability.ONLINE);
        }
        NewAddress dropoff = DROPOFFS.get(ThreadLocalRandom.current().nextInt(DROPOFFS.size()));
        ParcelDetails parcel = parcelService.createParcel(new NewParcel(courierUserId, null, null, PICKUP, dropoff,
                ParcelType.PARCEL, new BigDecimal("1.5"), null, null, null, "Sample parcel", "Call on arrival", null,
                new BigDecimal("450.00")));
        assignmentService.assign(parcel.id(), courierId, courierUserId);
        log.info("Dev: gave sample parcel {} to courier {}", parcel.id(), courierId);
        return new DevSampleDeliveryResponse(parcel.id(), parcel.trackingNumber());
    }
}
