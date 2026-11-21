package com.swiftdrop.parcel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.parcel.model.Parcel;
import com.swiftdrop.parcel.repository.DeliveryReceiptRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Who may download a receipt. The PDF itself is covered by ReceiptPdfRendererTest and the ITs. */
@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    @Mock private ParcelRepository parcelRepository;
    @Mock private DeliveryReceiptRepository receiptRepository;
    @Mock private ReceiptPdfRenderer renderer;
    @InjectMocks private ReceiptService receiptService;

    private final UUID merchantId = UUID.randomUUID();

    private static Parcel parcel(UUID merchantId, UUID customerUserId) {
        Parcel parcel = new Parcel();
        parcel.setId(UUID.randomUUID());
        parcel.setTrackingNumber("SD7KQ2MX9TRB4H");
        parcel.setMerchantId(merchantId);
        parcel.setCustomerUserId(customerUserId);
        return parcel;
    }

    @Test
    void merchantReceiptLinkPointsAtTheMerchantEndpoint() {
        assertThat(ReceiptService.merchantDocumentUrl("SD7KQ2MX9TRB4H"))
                .isEqualTo("/api/v1/merchant/orders/SD7KQ2MX9TRB4H/receipt");
    }

    @Test
    void merchantLooksUpTheOrderByItsNormalizedTrackingNumber() {
        when(parcelRepository.findByTrackingNumber("SD7KQ2MX9TRB4H")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> receiptService.receiptForMerchant(merchantId, "  sd7kq2mx9trb4h "))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Order not found");
    }

    @Test
    void anotherMerchantsOrderIsNotFoundAndNoReceiptIsRecorded() {
        when(parcelRepository.findByTrackingNumber("SD7KQ2MX9TRB4H"))
                .thenReturn(Optional.of(parcel(UUID.randomUUID(), null)));

        assertThatThrownBy(() -> receiptService.receiptForMerchant(merchantId, "SD7KQ2MX9TRB4H"))
                .isInstanceOf(NotFoundException.class);
        verify(parcelRepository, never()).findWithDetailsById(any());
        verifyNoInteractions(receiptRepository, renderer);
    }

    @Test
    void customerBookingIsNotFoundForAMerchant() {
        when(parcelRepository.findByTrackingNumber("SD7KQ2MX9TRB4H"))
                .thenReturn(Optional.of(parcel(null, UUID.randomUUID())));

        assertThatThrownBy(() -> receiptService.receiptForMerchant(merchantId, "SD7KQ2MX9TRB4H"))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(receiptRepository, renderer);
    }

    @Test
    void merchantOrderIsNotACustomersDelivery() {
        Parcel order = parcel(merchantId, null);
        when(parcelRepository.findWithDetailsById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> receiptService.receiptForCustomer(UUID.randomUUID(), order.getId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Delivery not found");
        verifyNoInteractions(receiptRepository, renderer);
    }
}
