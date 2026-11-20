package com.swiftdrop.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.dispatch.api.DispatchApi;
import com.swiftdrop.dispatch.dto.response.CourierResponse;
import com.swiftdrop.dispatch.dto.response.DevSampleDeliveryResponse;
import com.swiftdrop.dispatch.model.CourierAvailability;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.service.ParcelService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;

@ExtendWith(MockitoExtension.class)
class DevCourierDemoServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID COURIER = UUID.randomUUID();

    @Mock private DispatchApi dispatchApi;
    @Mock private CourierService courierService;
    @Mock private AssignmentService assignmentService;
    @Mock private ParcelService parcelService;
    @InjectMocks private DevCourierDemoService service;

    private static CourierResponse courierWith(CourierAvailability availability) {
        CourierResponse courier = mock(CourierResponse.class);
        when(courier.availability()).thenReturn(availability);
        return courier;
    }

    private ParcelDetails sampleParcel() {
        ParcelDetails parcel = mock(ParcelDetails.class);
        when(parcel.id()).thenReturn(UUID.randomUUID());
        when(parcel.trackingNumber()).thenReturn("SDSAMPLE0001");
        when(parcelService.createParcel(any())).thenReturn(parcel);
        return parcel;
    }

    @Test
    void onlyExistsInTheDevProfile() {
        assertThat(DevCourierDemoService.class.getAnnotation(Profile.class).value()).containsExactly("dev");
    }

    @Test
    void booksASampleParcelAndAssignsItToTheCourier() {
        when(dispatchApi.findCourierId(USER)).thenReturn(Optional.of(COURIER));
        CourierResponse online = courierWith(CourierAvailability.ONLINE);
        when(courierService.get(COURIER)).thenReturn(online);
        ParcelDetails parcel = sampleParcel();

        DevSampleDeliveryResponse response = service.createSampleDelivery(USER);

        ArgumentCaptor<NewParcel> booked = ArgumentCaptor.forClass(NewParcel.class);
        verify(parcelService).createParcel(booked.capture());
        assertThat(booked.getValue().customerUserId()).isEqualTo(USER);
        assertThat(booked.getValue().pickupAddress()).isEqualTo(DevCourierDemoService.PICKUP);
        assertThat(DevCourierDemoService.DROPOFFS).contains(booked.getValue().dropoffAddress());
        verify(assignmentService).assign(parcel.id(), COURIER, USER);
        verify(courierService, never()).changeAvailability(any(), any());
        assertThat(response.trackingNumber()).isEqualTo("SDSAMPLE0001");
        assertThat(response.parcelId()).isEqualTo(parcel.id());
    }

    @Test
    void setsAnOfflineCourierOnlineFirstSoTheAssignmentIsAllowed() {
        when(dispatchApi.findCourierId(USER)).thenReturn(Optional.of(COURIER));
        CourierResponse offline = courierWith(CourierAvailability.OFFLINE);
        when(courierService.get(COURIER)).thenReturn(offline);
        sampleParcel();

        service.createSampleDelivery(USER);

        verify(courierService).changeAvailability(COURIER, CourierAvailability.ONLINE);
    }

    @Test
    void anAccountWithoutACourierProfileGetsNotFound() {
        when(dispatchApi.findCourierId(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSampleDelivery(USER))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(DevCourierDemoService.NO_PROFILE);
        verifyNoInteractions(parcelService, assignmentService);
    }
}
