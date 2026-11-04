package com.swiftdrop.settings.dto.response;

import com.swiftdrop.settings.model.DateFormat;
import com.swiftdrop.settings.model.TimeFormat;
import java.math.BigDecimal;

/**
 * Settings anyone may see (no sign-in): contact details, display formats, parcel limits for the
 * booking form, and whether the system is in maintenance. Nothing internal.
 */
public record PublicSettingsResponse(
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
        boolean maintenanceMode) {
}
