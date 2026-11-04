package com.swiftdrop.settings.dto;

import com.swiftdrop.settings.model.DateFormat;
import com.swiftdrop.settings.model.FailedDeliveryAction;
import com.swiftdrop.settings.model.TimeFormat;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * All system settings. Returned to the admin, and the read model other modules use through
 * {@link com.swiftdrop.settings.service.SystemSettingsService#current()} (e.g. parcel limits for
 * booking, the failed delivery action for dispatch, notification switches for emails).
 *
 * @param version   pass back unchanged when saving; a different value means someone else saved first
 * @param updatedAt when the settings were last saved
 */
public record SystemSettingsView(
        String companyName,
        String contactEmail,
        String contactPhone,
        String companyAddress,
        String timeZone,
        DateFormat dateFormat,
        TimeFormat timeFormat,
        int itemsPerPage,
        String defaultParcelType,
        BigDecimal maxParcelWeightKg,
        BigDecimal maxParcelDimensionCm,
        FailedDeliveryAction failedDeliveryAction,
        boolean autoAssignCourier,
        boolean notifyFailedDeliveries,
        boolean notifyNewMerchants,
        boolean notifyCourierIssues,
        boolean dailySummaryReport,
        boolean maintenanceMode,
        long version,
        Instant updatedAt) {
}
