package com.swiftdrop.delivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.delivery.dto.request.UpdateDeliveryStatusRequest;
import com.swiftdrop.delivery.dto.response.CourierDeliveryCounts;
import com.swiftdrop.delivery.dto.response.CourierDeliveryDetailResponse;
import com.swiftdrop.delivery.dto.response.TodaysDeliveriesResponse;
import com.swiftdrop.delivery.service.CourierDeliveryService;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class CourierDeliveryControllerTest {

    private static final UUID COURIER_ID = UUID.randomUUID();
    private static final AuthenticatedUser COURIER = new AuthenticatedUser(COURIER_ID.toString(), Set.of(Role.COURIER));

    @Mock private CourierDeliveryService service;
    @InjectMocks private CourierDeliveryController controller;

    private static CourierDeliveryDetailResponse detail(UUID parcelId) {
        return new CourierDeliveryDetailResponse(parcelId, "SD1", null, null, ParcelType.PARCEL, BigDecimal.ONE,
                null, null, null, null, null, ParcelStatusCode.CREATED, List.of(ParcelStatusCode.PICKED_UP),
                Instant.now(), List.of());
    }

    @Test
    void onlyCouriersMayUseIt() {
        assertThat(CourierDeliveryController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('COURIER')");
        assertThat(CourierDeliveryController.PATH).isEqualTo("/api/v1/courier/deliveries");
    }

    @Test
    void readsForTheSignedInCourier() {
        UUID parcelId = UUID.randomUUID();
        TodaysDeliveriesResponse today = new TodaysDeliveriesResponse(new CourierDeliveryCounts(0, 0, 0), List.of());
        when(service.today(COURIER_ID)).thenReturn(today);
        when(service.get(parcelId, COURIER_ID)).thenReturn(detail(parcelId));
        when(service.getByTrackingNumber("sd1", COURIER_ID)).thenReturn(detail(parcelId));

        assertThat(controller.today(COURIER)).isSameAs(today);
        assertThat(controller.get(COURIER, parcelId).parcelId()).isEqualTo(parcelId);
        assertThat(controller.getByTrackingNumber(COURIER, "sd1").parcelId()).isEqualTo(parcelId);
    }

    @Test
    void updatesTheStatusAsTheSignedInCourier() {
        UUID parcelId = UUID.randomUUID();
        UpdateDeliveryStatusRequest request = new UpdateDeliveryStatusRequest(ParcelStatusCode.PICKED_UP, null);
        when(service.updateStatus(parcelId, COURIER_ID, request)).thenReturn(detail(parcelId));

        assertThat(controller.updateStatus(COURIER, parcelId, request).parcelId()).isEqualTo(parcelId);
    }
}
