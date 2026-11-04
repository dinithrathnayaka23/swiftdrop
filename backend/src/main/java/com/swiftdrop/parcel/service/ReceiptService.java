package com.swiftdrop.parcel.service;

import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.parcel.mapper.ParcelViewMapper;
import com.swiftdrop.parcel.model.DeliveryReceipt;
import com.swiftdrop.parcel.model.Parcel;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.repository.DeliveryReceiptRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivery receipts. The PDF is drawn on every download, so it always shows the current status;
 * the first download records the receipt number in {@code delivery_receipts}, and later downloads
 * reuse it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {

    /** A receipt ready to send: the file name and the PDF bytes. */
    public record ReceiptDocument(String fileName, byte[] pdf) {
    }

    private final ParcelRepository parcelRepository;
    private final DeliveryReceiptRepository receiptRepository;
    private final ReceiptPdfRenderer renderer;

    /** "SDR-" and the tracking number without its "SD" prefix: unique because tracking numbers are. */
    static String receiptNumber(String trackingNumber) {
        return "SDR-" + (trackingNumber.startsWith("SD") ? trackingNumber.substring(2) : trackingNumber);
    }

    /** The URL the receipt is downloaded from, stored as the receipt's {@code document_url}. */
    static String documentUrl(UUID parcelId) {
        return ApiPaths.API_V1 + "/customer/parcels/" + parcelId + "/receipt";
    }

    /**
     * The receipt of one of the customer's own parcels.
     *
     * @throws NotFoundException if the parcel doesn't exist or belongs to someone else
     */
    @Transactional
    public ReceiptDocument receiptForCustomer(UUID customerUserId, UUID parcelId) {
        Parcel parcel = parcelRepository.findWithDetailsById(parcelId)
                .filter(found -> customerUserId.equals(found.getCustomerUserId()))
                .orElseThrow(() -> new NotFoundException("Delivery not found"));

        receiptRepository.insertIfAbsent(parcelId, receiptNumber(parcel.getTrackingNumber()), documentUrl(parcelId));
        DeliveryReceipt receipt = receiptRepository.findByParcelId(parcelId).orElseThrow();

        byte[] pdf = renderer.render(new ReceiptContent(receipt.getReceiptNumber(), receipt.getGeneratedAt(),
                parcel.getTrackingNumber(), parcel.getExternalOrderReference(), parcel.getCreatedAt(),
                ParcelStatusCode.valueOf(parcel.getCurrentStatus().getStatusCode()),
                ParcelViewMapper.toAddress(parcel.getPickupAddress()),
                ParcelViewMapper.toAddress(parcel.getDropoffAddress()), parcel.getParcelType(),
                parcel.getContentDescription(), parcel.getWeightKg(), parcel.getLengthCm(), parcel.getWidthCm(),
                parcel.getHeightCm(), parcel.getQuotedFee(), parcel.getFeeCurrency()));
        log.info("Receipt {} downloaded for parcel {}", receipt.getReceiptNumber(), parcelId);
        return new ReceiptDocument("receipt-" + parcel.getTrackingNumber() + ".pdf", pdf);
    }
}
