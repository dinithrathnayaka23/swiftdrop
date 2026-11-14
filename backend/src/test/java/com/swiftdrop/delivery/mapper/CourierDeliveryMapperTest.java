package com.swiftdrop.delivery.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CourierDeliveryMapperTest {

    @ParameterizedTest
    @EnumSource(value = ParcelStatusCode.class, names = {"PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"})
    void aCourierOnTheWayMayReportAFailedAttemptButNeverFailTheParcel(ParcelStatusCode current) {
        assertThat(CourierDeliveryMapper.allowedActions(current))
                .contains(ParcelStatusCode.FAILED_ATTEMPT).doesNotContain(ParcelStatusCode.FAILED);
    }

    @Test
    void theRetryAndTheCloseOfAFailedAttemptAreTheDispatchers() {
        assertThat(ParcelStatusCode.FAILED_ATTEMPT.nextStatuses())
                .containsExactlyInAnyOrder(ParcelStatusCode.CREATED, ParcelStatusCode.FAILED);
        assertThat(CourierDeliveryMapper.allowedActions(ParcelStatusCode.FAILED_ATTEMPT)).isEmpty();
    }

    @Test
    void actionsKeepLifecycleOrder() {
        assertThat(CourierDeliveryMapper.allowedActions(ParcelStatusCode.OUT_FOR_DELIVERY))
                .containsExactly(ParcelStatusCode.DELIVERED, ParcelStatusCode.FAILED_ATTEMPT);
        assertThat(CourierDeliveryMapper.allowedActions(ParcelStatusCode.CREATED))
                .containsExactly(ParcelStatusCode.PICKED_UP);
    }
}
