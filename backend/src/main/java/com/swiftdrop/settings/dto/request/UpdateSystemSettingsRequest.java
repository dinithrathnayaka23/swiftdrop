package com.swiftdrop.settings.dto.request;

import com.swiftdrop.settings.model.DateFormat;
import com.swiftdrop.settings.model.FailedDeliveryAction;
import com.swiftdrop.settings.model.TimeFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Saves every setting at once (the whole form).
 *
 * @param version the version the form was loaded with; saving fails with 409 if it has changed
 */
public record UpdateSystemSettingsRequest(
        @NotNull(message = "Version is required")
        Long version,

        @NotBlank(message = "Company name is required")
        @Size(max = 120, message = "Company name must be at most 120 characters")
        String companyName,

        @NotBlank(message = "Email is required")
        // @Email alone accepts addresses without a domain ending ("support@swiftdrop"), which would
        // show as a broken link in the footer. Same rule as validateEmail in the frontend.
        @Email(message = "Enter a valid email address")
        @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$",
                message = "Enter a valid email address")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String contactEmail,

        // Optional; E.164 when given, e.g. +94771234567.
        @Pattern(regexp = "^$|^\\+[1-9]\\d{6,14}$", message = "Enter a valid phone number")
        String contactPhone,

        @Size(max = 255, message = "Address must be at most 255 characters")
        String companyAddress,

        @NotBlank(message = "Time zone is required")
        @Size(max = 50, message = "Enter a valid time zone")
        String timeZone,

        @NotNull(message = "Date format is required")
        DateFormat dateFormat,

        @NotNull(message = "Time format is required")
        TimeFormat timeFormat,

        @NotNull(message = "Items per page is required")
        Integer itemsPerPage,

        @NotBlank(message = "Default parcel type is required")
        @Pattern(regexp = "DOCUMENT|PARCEL|FRAGILE|ELECTRONICS|PERISHABLE", message = "Choose a parcel type")
        String defaultParcelType,

        @NotNull(message = "Maximum parcel weight is required")
        @DecimalMin(value = "0", inclusive = false, message = "Maximum parcel weight must be more than 0")
        @DecimalMax(value = "1000", message = "Maximum parcel weight can be at most 1000 kg")
        @Digits(integer = 4, fraction = 3, message = "Use at most 3 decimal places")
        BigDecimal maxParcelWeightKg,

        @NotNull(message = "Maximum dimension is required")
        @DecimalMin(value = "0", inclusive = false, message = "Maximum dimension must be more than 0")
        @DecimalMax(value = "1000", message = "Maximum dimension can be at most 1000 cm")
        @Digits(integer = 4, fraction = 2, message = "Use at most 2 decimal places")
        BigDecimal maxParcelDimensionCm,

        @NotNull(message = "Failed delivery action is required")
        FailedDeliveryAction failedDeliveryAction,

        @NotNull Boolean autoAssignCourier,
        @NotNull Boolean notifyFailedDeliveries,
        @NotNull Boolean notifyNewMerchants,
        @NotNull Boolean notifyCourierIssues,
        @NotNull Boolean dailySummaryReport,
        @NotNull Boolean maintenanceMode) {
}
