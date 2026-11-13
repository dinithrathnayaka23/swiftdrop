package com.swiftdrop.delivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.delivery.dto.request.ReportFailureRequest;
import com.swiftdrop.delivery.dto.response.CourierDeliveryDetailResponse;
import com.swiftdrop.delivery.dto.response.FailureReasonResponse;
import com.swiftdrop.delivery.service.DeliveryFailureService;
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
class CourierFailureControllerTest {

    private static final UUID COURIER_ID = UUID.randomUUID();
    private static final AuthenticatedUser COURIER = new AuthenticatedUser(COURIER_ID.toString(), Set.of(Role.COURIER));

    @Mock private DeliveryFailureService service;
    @InjectMocks private CourierFailureController controller;

    @Test
    void onlyCouriersMayUseIt() {
        assertThat(CourierFailureController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('COURIER')");
        assertThat(CourierFailureController.PATH).isEqualTo("/api/v1/courier");
    }

    @Test
    void listsTheReasons() {
        List<FailureReasonResponse> reasons = List.of(new FailureReasonResponse("OTHER", "Other", true));
        when(service.reasons()).thenReturn(reasons);

        assertThat(controller.reasons()).isSameAs(reasons);
    }

    @Test
    void reportsTheFailureAsTheSignedInCourier() {
        UUID parcelId = UUID.randomUUID();
        ReportFailureRequest request = new ReportFailureRequest("REFUSED", null);
        CourierDeliveryDetailResponse detail = new CourierDeliveryDetailResponse(parcelId, "SD1", null, null,
                ParcelType.PARCEL, BigDecimal.ONE, null, null, null, null, null, ParcelStatusCode.FAILED, List.of(),
                Instant.now(), List.of());
        when(service.reportFailure(parcelId, COURIER_ID, request)).thenReturn(detail);

        assertThat(controller.reportFailure(COURIER, parcelId, request)).isSameAs(detail);
    }
}
