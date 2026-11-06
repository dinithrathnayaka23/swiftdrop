package com.swiftdrop.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.dto.StatusChange;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.service.ParcelService;
import com.swiftdrop.parcel.service.ParcelStatusService;
import com.swiftdrop.tracking.controller.DevTrackingController;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@ExtendWith(MockitoExtension.class)
class DevTrackingDemoServiceTest {

    private static final String NUMBER = "SD12345678";

    @Mock private ParcelService parcelService;
    @Mock private ParcelStatusService parcelStatusService;

    private DevTrackingDemoService service;
    private final UUID parcelId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DevTrackingDemoService(parcelService, parcelStatusService);
    }

    private void parcelIs(ParcelStatusCode status) {
        ParcelDetails parcel = new ParcelDetails(parcelId, NUMBER, UUID.randomUUID(), null, null, null, null, null,
                null, null, null, null, null, null, status, null, null, null, null, null);
        when(parcelService.findByTrackingNumber(NUMBER)).thenReturn(Optional.of(parcel));
    }

    @ParameterizedTest
    @CsvSource({"CREATED, PICKED_UP", "PICKED_UP, IN_TRANSIT", "IN_TRANSIT, OUT_FOR_DELIVERY",
            "OUT_FOR_DELIVERY, DELIVERED"})
    void advanceMovesToTheNextNormalStatus(ParcelStatusCode current, ParcelStatusCode expected) {
        parcelIs(current);

        service.advance(NUMBER, null, adminId);

        StatusChange change = expected == ParcelStatusCode.DELIVERED
                ? StatusChange.delivered(adminId, DevTrackingDemoService.RECIPIENT_NAME, null, null)
                : StatusChange.to(expected, adminId, null);
        verify(parcelStatusService).changeStatus(parcelId, change);
    }

    @Test
    void advanceToFailedSendsAFailureReason() {
        parcelIs(ParcelStatusCode.IN_TRANSIT);

        service.advance(NUMBER, ParcelStatusCode.FAILED, adminId);

        verify(parcelStatusService).changeStatus(parcelId,
                StatusChange.failed(adminId, DevTrackingDemoService.FAILURE_REASON, null));
    }

    @ParameterizedTest
    @EnumSource(value = ParcelStatusCode.class, names = {"DELIVERED", "FAILED"})
    void anEndStateIsAConflict(ParcelStatusCode status) {
        parcelIs(status);

        assertThatThrownBy(() -> service.advance(NUMBER, null, adminId)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(parcelStatusService);
    }

    @Test
    void onlyFailedCanBeChosenAsTheTarget() {
        parcelIs(ParcelStatusCode.CREATED);

        assertThatThrownBy(() -> service.advance(NUMBER, ParcelStatusCode.DELIVERED, adminId))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(parcelStatusService);
    }

    @Test
    void anUnknownNumberIsNotFound() {
        when(parcelService.findByTrackingNumber(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.advance("SD00000000", null, adminId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void theDemoHelperExistsOnlyUnderTheDevProfile() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(ParcelService.class, () -> mock(ParcelService.class))
                .withBean(ParcelStatusService.class, () -> mock(ParcelStatusService.class))
                .withBean(PublicTrackingService.class, () -> mock(PublicTrackingService.class))
                .withUserConfiguration(DevTrackingDemoService.class, DevTrackingController.class);

        runner.run(context -> {
            assertThat(context).doesNotHaveBean(DevTrackingDemoService.class);
            assertThat(context).doesNotHaveBean(DevTrackingController.class);
        });
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("dev")).run(context -> {
            assertThat(context).hasSingleBean(DevTrackingDemoService.class);
            assertThat(context).hasSingleBean(DevTrackingController.class);
        });
    }
}
