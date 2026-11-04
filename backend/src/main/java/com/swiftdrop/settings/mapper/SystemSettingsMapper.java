package com.swiftdrop.settings.mapper;

import com.swiftdrop.settings.dto.SystemSettingsView;
import com.swiftdrop.settings.dto.response.PublicSettingsResponse;
import com.swiftdrop.settings.model.SystemSettings;

public final class SystemSettingsMapper {

    private SystemSettingsMapper() {
    }

    public static SystemSettingsView toView(SystemSettings s) {
        return new SystemSettingsView(s.getCompanyName(), s.getContactEmail(), s.getContactPhone(),
                s.getCompanyAddress(), s.getTimeZone(), s.getDateFormat(), s.getTimeFormat(), s.getItemsPerPage(),
                s.getDefaultParcelType(), s.getMaxParcelWeightKg(), s.getMaxParcelDimensionCm(),
                s.getFailedDeliveryAction(), s.isAutoAssignCourier(), s.isNotifyFailedDeliveries(),
                s.isNotifyNewMerchants(), s.isNotifyCourierIssues(), s.isDailySummaryReport(),
                s.isMaintenanceMode(), s.getVersion(), s.getUpdatedAt());
    }

    public static PublicSettingsResponse toPublic(SystemSettingsView v) {
        return new PublicSettingsResponse(v.companyName(), v.contactEmail(), v.contactPhone(), v.companyAddress(),
                v.timeZone(), v.dateFormat(), v.timeFormat(), v.itemsPerPage(), v.defaultParcelType(),
                v.maxParcelWeightKg(), v.maxParcelDimensionCm(), v.maintenanceMode());
    }
}
