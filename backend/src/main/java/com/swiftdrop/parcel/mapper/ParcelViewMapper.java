package com.swiftdrop.parcel.mapper;

import com.swiftdrop.parcel.dto.response.AddressResponse;
import com.swiftdrop.parcel.dto.response.AdminParcelDetailResponse;
import com.swiftdrop.parcel.dto.response.AdminParcelRowResponse;
import com.swiftdrop.parcel.dto.response.CustomerParcelDetailResponse;
import com.swiftdrop.parcel.dto.response.CustomerParcelSummaryResponse;
import com.swiftdrop.parcel.dto.response.StatusHistoryItemResponse;
import com.swiftdrop.parcel.model.Address;
import com.swiftdrop.parcel.model.Parcel;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelStatusHistory;
import java.util.List;

/** Maps parcels to the responses of the customer and admin parcel screens. */
public final class ParcelViewMapper {

    private ParcelViewMapper() {
    }

    public static AddressResponse toAddress(Address address) {
        return new AddressResponse(address.getContactName(), address.getContactPhone(), address.getAddressLine1(),
                address.getAddressLine2(), address.getCity(), address.getDistrict(), address.getPostalCode());
    }

    public static StatusHistoryItemResponse toHistoryItem(ParcelStatusHistory entry) {
        return new StatusHistoryItemResponse(ParcelStatusCode.valueOf(entry.getStatus().getStatusCode()),
                entry.getChangedAt(), entry.getNote(), entry.getFailureReason());
    }

    public static CustomerParcelSummaryResponse toCustomerSummary(Parcel parcel) {
        Address dropoff = parcel.getDropoffAddress();
        return new CustomerParcelSummaryResponse(parcel.getId(), parcel.getTrackingNumber(),
                parcel.getExternalOrderReference(), dropoff.getCity(), dropoff.getDistrict(), parcel.getCreatedAt(),
                status(parcel), parcel.getQuotedFee(), parcel.getFeeCurrency());
    }

    public static CustomerParcelDetailResponse toCustomerDetail(Parcel parcel, List<ParcelStatusHistory> history) {
        return new CustomerParcelDetailResponse(parcel.getId(), parcel.getTrackingNumber(),
                parcel.getExternalOrderReference(), parcel.getCreatedAt(), parcel.getUpdatedAt(), status(parcel),
                toAddress(parcel.getPickupAddress()), toAddress(parcel.getDropoffAddress()), parcel.getParcelType(),
                parcel.getContentDescription(), parcel.getWeightKg(), parcel.getLengthCm(), parcel.getWidthCm(),
                parcel.getHeightCm(), parcel.getDeliveryInstructions(), parcel.getQuotedFee(), null,
                parcel.getFeeCurrency(), null, null, history.stream().map(ParcelViewMapper::toHistoryItem).toList());
    }

    public static AdminParcelRowResponse toAdminRow(Parcel parcel, String merchantName, String dispatchState) {
        Address dropoff = parcel.getDropoffAddress();
        return new AdminParcelRowResponse(parcel.getId(), parcel.getTrackingNumber(), merchantName,
                dropoff.getContactName(), dropoff.getCity(), parcel.getWeightKg(), status(parcel), dispatchState,
                parcel.getCreatedAt());
    }

    public static AdminParcelDetailResponse toAdminDetail(Parcel parcel, String merchantName, String dispatchState,
                                                          List<ParcelStatusHistory> history) {
        Address dropoff = parcel.getDropoffAddress();
        return new AdminParcelDetailResponse(parcel.getId(), parcel.getTrackingNumber(), merchantName,
                dropoff.getContactName(), dropoff.getCity(), parcel.getWeightKg(), status(parcel), dispatchState,
                parcel.getCreatedAt(), null, parcel.getParcelType(), parcel.getContentDescription(), 1, null,
                dropoff.getContactPhone(), null, toAddress(dropoff), parcel.getDeliveryInstructions(),
                history.stream().map(ParcelViewMapper::toHistoryItem).toList(), List.of());
    }

    private static ParcelStatusCode status(Parcel parcel) {
        return ParcelStatusCode.valueOf(parcel.getCurrentStatus().getStatusCode());
    }
}
