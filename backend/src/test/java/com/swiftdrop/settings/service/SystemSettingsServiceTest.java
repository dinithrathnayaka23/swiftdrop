package com.swiftdrop.settings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.ApiException;
import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.ErrorCode;
import com.swiftdrop.settings.dto.SystemSettingsView;
import com.swiftdrop.settings.dto.request.UpdateSystemSettingsRequest;
import com.swiftdrop.settings.exception.InvalidSettingException;
import com.swiftdrop.settings.model.DateFormat;
import com.swiftdrop.settings.model.FailedDeliveryAction;
import com.swiftdrop.settings.model.SystemSettings;
import com.swiftdrop.settings.model.TimeFormat;
import com.swiftdrop.settings.repository.SystemSettingsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-04T08:00:00Z");

    @Mock private SystemSettingsRepository repository;

    private final AtomicReference<Instant> now = new AtomicReference<>(T0);
    private final UUID adminId = UUID.randomUUID();
    private SystemSettingsService service;
    private SystemSettings stored;

    @BeforeEach
    void setUp() {
        service = new SystemSettingsService(repository, now::get);
        stored = defaults();
    }

    /** The row as V1_001 creates it. */
    private static SystemSettings defaults() {
        SystemSettings s = new SystemSettings();
        s.setId(SystemSettings.ID);
        s.setCompanyName("SwiftDrop");
        s.setContactEmail("support@swiftdrop.lk");
        s.setTimeZone("Asia/Colombo");
        s.setDateFormat(DateFormat.DD_MM_YYYY);
        s.setTimeFormat(TimeFormat.H12);
        s.setItemsPerPage((short) 10);
        s.setDefaultParcelType("PARCEL");
        s.setMaxParcelWeightKg(new BigDecimal("50"));
        s.setMaxParcelDimensionCm(new BigDecimal("120"));
        s.setFailedDeliveryAction(FailedDeliveryAction.RETRY_DELIVERY);
        s.setNotifyFailedDeliveries(true);
        s.setNotifyNewMerchants(true);
        s.setNotifyCourierIssues(true);
        s.setVersion(3);
        s.setUpdatedAt(T0.minusSeconds(3600));
        return s;
    }

    private static UpdateSystemSettingsRequest request(long version, String timeZone, int itemsPerPage,
                                                       boolean maintenance) {
        return new UpdateSystemSettingsRequest(version, "  SwiftDrop Lanka ", " Help@SwiftDrop.lk ", " ",
                "No. 15, Park Road, Colombo 07", timeZone, DateFormat.YYYY_MM_DD, TimeFormat.H24, itemsPerPage,
                "FRAGILE", new BigDecimal("30.5"), new BigDecimal("100"), FailedDeliveryAction.RETURN_TO_SENDER,
                true, false, true, false, true, maintenance);
    }

    @Test
    void readsAreCachedForAFewSeconds() {
        when(repository.findById(SystemSettings.ID)).thenReturn(Optional.of(stored));

        service.current();
        service.maintenanceMode();
        service.publicSettings();
        verify(repository, times(1)).findById(SystemSettings.ID);

        now.set(T0.plus(SystemSettingsService.CACHE_TTL).plusSeconds(1));
        service.current();
        verify(repository, times(2)).findById(SystemSettings.ID);
    }

    @Test
    void theAdminAlwaysReadsFreshValues() {
        when(repository.findById(SystemSettings.ID)).thenReturn(Optional.of(stored));

        service.forAdmin();
        service.forAdmin();

        verify(repository, times(2)).findById(SystemSettings.ID);
    }

    @Test
    void publicSettingsLeaveOutInternalOnes() {
        when(repository.findById(SystemSettings.ID)).thenReturn(Optional.of(stored));

        var publicSettings = service.publicSettings();

        assertThat(publicSettings.contactEmail()).isEqualTo("support@swiftdrop.lk");
        assertThat(publicSettings.maxParcelWeightKg()).isEqualByComparingTo("50");
        assertThat(publicSettings.maintenanceMode()).isFalse();
        // Record components: no version, no notification switches, no failed-delivery action.
        assertThat(publicSettings.getClass().getRecordComponents())
                .extracting(c -> c.getName())
                .doesNotContain("version", "notifyFailedDeliveries", "failedDeliveryAction", "updatedAt");
    }

    @Test
    void savesTheWholeFormCleaningUpText() {
        when(repository.findById(SystemSettings.ID)).thenReturn(Optional.of(stored));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        now.set(T0.plusSeconds(60));

        SystemSettingsView saved = service.update(request(3, "Asia/Colombo", 20, false), adminId);

        assertThat(saved.companyName()).isEqualTo("SwiftDrop Lanka");
        assertThat(saved.contactEmail()).isEqualTo("help@swiftdrop.lk");
        assertThat(saved.contactPhone()).isNull(); // blank -> not set
        assertThat(saved.dateFormat()).isEqualTo(DateFormat.YYYY_MM_DD);
        assertThat(saved.itemsPerPage()).isEqualTo(20);
        assertThat(saved.defaultParcelType()).isEqualTo("FRAGILE");
        assertThat(saved.failedDeliveryAction()).isEqualTo(FailedDeliveryAction.RETURN_TO_SENDER);
        assertThat(saved.autoAssignCourier()).isTrue();
        assertThat(saved.notifyFailedDeliveries()).isFalse();
        assertThat(saved.updatedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(stored.getUpdatedByUserId()).isEqualTo(adminId);
    }

    @Test
    void aSaveIsSeenStraightAwayDespiteTheCache() {
        when(repository.findById(SystemSettings.ID)).thenReturn(Optional.of(stored));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.maintenanceMode()).isFalse(); // now cached

        service.update(request(3, "Asia/Colombo", 10, true), adminId);

        assertThat(service.maintenanceMode()).isTrue();
    }

    @Test
    void refusesToOverwriteChangesSavedSinceTheFormWasLoaded() {
        when(repository.findById(SystemSettings.ID)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.update(request(2, "Asia/Colombo", 10, false), adminId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Reload");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void aSaveThatLosesARaceIsAConflictNotAnOutage() {
        when(repository.findById(SystemSettings.ID)).thenReturn(Optional.of(stored));
        // Another admin's save committed between our version check and our write.
        when(repository.saveAndFlush(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(SystemSettings.class, SystemSettings.ID));

        assertThatThrownBy(() -> service.update(request(3, "Asia/Colombo", 10, false), adminId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Reload");
    }

    @Test
    void rejectsAnUnknownTimeZoneOnThatField() {
        when(repository.findById(SystemSettings.ID)).thenReturn(Optional.of(stored));

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.update(request(3, "Mars/Olympus", 10, false), adminId));

        assertThat(thrown).isInstanceOf(InvalidSettingException.class);
        assertThat(((ApiException) thrown).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(((ApiException) thrown).getFieldErrors()).singleElement()
                .satisfies(error -> assertThat(error.field()).isEqualTo("timeZone"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAPageSizeTheListsDoNotOffer() {
        when(repository.findById(SystemSettings.ID)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.update(request(3, "Asia/Colombo", 15, false), adminId))
                .isInstanceOf(InvalidSettingException.class)
                .hasMessageContaining("10, 20, 50 or 100");
    }

    @Test
    void aMissingSettingsRowIsAServerProblem() {
        when(repository.findById(SystemSettings.ID)).thenReturn(Optional.empty());

        assertThatThrownBy(service::current).isInstanceOf(IllegalStateException.class);
    }
}
