package com.swiftdrop.parcel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.swiftdrop.common.exception.ConflictException;
import com.swiftdrop.common.exception.NotFoundException;
import com.swiftdrop.parcel.dto.NewAddress;
import com.swiftdrop.parcel.dto.NewParcel;
import com.swiftdrop.parcel.dto.ParcelDetails;
import com.swiftdrop.parcel.model.Address;
import com.swiftdrop.parcel.model.Parcel;
import com.swiftdrop.parcel.model.ParcelStatus;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelStatusHistory;
import com.swiftdrop.parcel.model.ParcelType;
import com.swiftdrop.parcel.repository.AddressRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import com.swiftdrop.parcel.repository.ParcelStatusRepository;
import com.swiftdrop.parcel.repository.StatusCount;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ParcelServiceTest {

    @Mock private ParcelRepository parcelRepository;
    @Mock private ParcelStatusRepository parcelStatusRepository;
    @Mock private ParcelStatusHistoryRepository statusHistoryRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private TrackingNumberGenerator trackingNumberGenerator;

    private ParcelService parcelService;
    private final UUID customerId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        parcelService = new ParcelService(parcelRepository, parcelStatusRepository, statusHistoryRepository,
                addressRepository, trackingNumberGenerator);
    }

    private static NewAddress address(String contactName, String city) {
        return new NewAddress(contactName, "+94771234567", "12 Galle Road", null, city, "Colombo", "00300", null);
    }

    private NewParcel customerParcel() {
        return new NewParcel(customerId, null, null, address("Nimal Perera", "Colombo"),
                address("Kamala Silva", "Kandy"), ParcelType.DOCUMENT, new BigDecimal("2.500"), null, null, null,
                "Books", null, null, new BigDecimal("450.00"));
    }

    private NewParcel merchantParcel(String externalOrderReference) {
        return new NewParcel(null, merchantId, externalOrderReference, address("BrightMart", "Colombo"),
                address("Kamala Silva", "Kandy"), ParcelType.DOCUMENT, new BigDecimal("1.000"), null, null, null,
                null, null, null, new BigDecimal("350.00"));
    }

    private static ParcelStatus status(ParcelStatusCode code) {
        ParcelStatus status = new ParcelStatus();
        status.setStatusCode(code.name());
        return status;
    }

    private void stubSaving() {
        when(parcelStatusRepository.getReferenceById("CREATED")).thenReturn(status(ParcelStatusCode.CREATED));
        when(addressRepository.save(any(Address.class))).thenAnswer(call -> call.getArgument(0));
        when(parcelRepository.saveAndFlush(any(Parcel.class))).thenAnswer(call -> {
            Parcel parcel = call.getArgument(0);
            parcel.setId(UUID.randomUUID());
            return parcel;
        });
    }

    private static Parcel savedParcel(String trackingNumber) {
        Parcel parcel = new Parcel();
        parcel.setId(UUID.randomUUID());
        parcel.setTrackingNumber(trackingNumber);
        parcel.setPickupAddress(new Address());
        parcel.setDropoffAddress(new Address());
        parcel.setCurrentStatus(status(ParcelStatusCode.IN_TRANSIT));
        parcel.setParcelType(ParcelType.PARCEL);
        parcel.setWeightKg(BigDecimal.ONE);
        parcel.setQuotedFee(BigDecimal.TEN);
        return parcel;
    }

    @Test
    void createsACustomerParcelWithStatusCreatedAndAFirstHistoryEntry() {
        stubSaving();
        when(trackingNumberGenerator.next()).thenReturn("SD7KQ2MX9TRB4H");

        ParcelDetails details = parcelService.createParcel(customerParcel());

        assertThat(details.trackingNumber()).isEqualTo("SD7KQ2MX9TRB4H");
        assertThat(details.currentStatus()).isEqualTo(ParcelStatusCode.CREATED);
        assertThat(details.parcelType()).isEqualTo(ParcelType.DOCUMENT);
        assertThat(details.customerUserId()).isEqualTo(customerId);
        assertThat(details.merchantId()).isNull();
        assertThat(details.pickupAddress().city()).isEqualTo("Colombo");
        assertThat(details.dropoffAddress().city()).isEqualTo("Kandy");
        assertThat(details.feeCurrency()).isEqualTo("LKR");

        ArgumentCaptor<ParcelStatusHistory> history = ArgumentCaptor.forClass(ParcelStatusHistory.class);
        verify(statusHistoryRepository).save(history.capture());
        assertThat(history.getValue().getPreviousStatus()).isNull();
        assertThat(history.getValue().getStatus().getStatusCode()).isEqualTo("CREATED");
        assertThat(history.getValue().getChangedByUserId()).isEqualTo(customerId);
        assertThat(history.getValue().getParcel().getId()).isEqualTo(details.id());
    }

    @Test
    void merchantParcelHistoryHasNoUserBecauseItIsASystemChange() {
        stubSaving();
        when(trackingNumberGenerator.next()).thenReturn("SD7KQ2MX9TRB4H");

        ParcelDetails details = parcelService.createParcel(merchantParcel("  ORD-1001 "));

        assertThat(details.merchantId()).isEqualTo(merchantId);
        assertThat(details.externalOrderReference()).isEqualTo("ORD-1001");
        ArgumentCaptor<ParcelStatusHistory> history = ArgumentCaptor.forClass(ParcelStatusHistory.class);
        verify(statusHistoryRepository).save(history.capture());
        assertThat(history.getValue().getChangedByUserId()).isNull();
    }

    @Test
    void trimsTextAndStoresBlankOptionalFieldsAsNull() {
        stubSaving();
        when(trackingNumberGenerator.next()).thenReturn("SD7KQ2MX9TRB4H");
        NewAddress messy = new NewAddress("  Nimal Perera ", " +94771234567 ", " 12 Galle Road ", "   ",
                " Colombo ", " Colombo ", "", null);
        NewParcel newParcel = new NewParcel(customerId, null, null, messy, messy, ParcelType.PARCEL, BigDecimal.ONE, null, null,
                null, "  ", " Leave at the gate ", null, BigDecimal.TEN);

        ParcelDetails details = parcelService.createParcel(newParcel);

        assertThat(details.pickupAddress().contactName()).isEqualTo("Nimal Perera");
        assertThat(details.pickupAddress().addressLine2()).isNull();
        assertThat(details.pickupAddress().postalCode()).isNull();
        assertThat(details.contentDescription()).isNull();
        assertThat(details.deliveryInstructions()).isEqualTo("Leave at the gate");
    }

    @Test
    void triesAnotherTrackingNumberWhenOneIsTaken() {
        stubSaving();
        when(trackingNumberGenerator.next()).thenReturn("SDTAKEN0000000", "SDFREE22222222");
        when(parcelRepository.existsByTrackingNumber("SDTAKEN0000000")).thenReturn(true);

        assertThat(parcelService.createParcel(customerParcel()).trackingNumber()).isEqualTo("SDFREE22222222");
    }

    @Test
    void givesUpAfterSeveralTakenTrackingNumbers() {
        when(parcelStatusRepository.getReferenceById("CREATED")).thenReturn(status(ParcelStatusCode.CREATED));
        when(trackingNumberGenerator.next()).thenReturn("SDTAKEN0000000");
        when(parcelRepository.existsByTrackingNumber("SDTAKEN0000000")).thenReturn(true);

        assertThatThrownBy(() -> parcelService.createParcel(customerParcel()))
                .isInstanceOf(IllegalStateException.class);
        verify(trackingNumberGenerator, times(ParcelService.TRACKING_NUMBER_ATTEMPTS)).next();
        verify(parcelRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsADuplicateMerchantOrderReference() {
        when(parcelRepository.existsByMerchantIdAndExternalOrderReference(merchantId, "ORD-1001")).thenReturn(true);

        assertThatThrownBy(() -> parcelService.createParcel(merchantParcel("ORD-1001")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An order with this reference already exists");
        verify(parcelRepository, never()).saveAndFlush(any());
        verifyNoInteractions(addressRepository, statusHistoryRepository);
    }

    @Test
    void newParcelNeedsExactlyOneOwner() {
        NewAddress address = address("Nimal Perera", "Colombo");
        assertThatThrownBy(() -> new NewParcel(null, null, null, address, address, ParcelType.PARCEL, BigDecimal.ONE, null, null,
                null, null, null, null, BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NewParcel(customerId, merchantId, null, address, address, ParcelType.PARCEL, BigDecimal.ONE,
                null, null, null, null, null, null, BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newParcelRejectsAnOrderReferenceOnACustomerBooking() {
        NewAddress address = address("Nimal Perera", "Colombo");
        assertThatThrownBy(() -> new NewParcel(customerId, null, "ORD-1", address, address, ParcelType.PARCEL, BigDecimal.ONE,
                null, null, null, null, null, null, BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newParcelRejectsZeroWeightAndNegativeFee() {
        NewAddress address = address("Nimal Perera", "Colombo");
        assertThatThrownBy(() -> new NewParcel(customerId, null, null, address, address, ParcelType.PARCEL, BigDecimal.ZERO,
                null, null, null, null, null, null, BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NewParcel(customerId, null, null, address, address, ParcelType.PARCEL, BigDecimal.ONE,
                null, null, null, null, null, null, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newParcelNeedsAParcelType() {
        NewAddress address = address("Nimal Perera", "Colombo");
        assertThatThrownBy(() -> new NewParcel(customerId, null, null, address, address, null, BigDecimal.ONE,
                null, null, null, null, null, null, BigDecimal.TEN)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void findAllByIdsLoadsTheParcelsInOneQuery() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(parcelRepository.findWithDetailsByIdIn(List.of(first, second)))
                .thenReturn(List.of(savedParcel("SDAAAAAAAAAAAA"), savedParcel("SDBBBBBBBBBBBB")));

        assertThat(parcelService.findAllByIds(List.of(first, second)))
                .extracting(ParcelDetails::trackingNumber).containsExactly("SDAAAAAAAAAAAA", "SDBBBBBBBBBBBB");
    }

    @Test
    void findAllByIdsWithNoIdsDoesNotQuery() {
        assertThat(parcelService.findAllByIds(List.of())).isEmpty();
        verifyNoInteractions(parcelRepository);
    }

    @Test
    void findsAMerchantOrderByItsTrimmedReference() {
        when(parcelRepository.findByMerchantIdAndExternalOrderReference(merchantId, "ORD-1001"))
                .thenReturn(Optional.of(savedParcel("SD7KQ2MX9TRB4H")));

        assertThat(parcelService.findByMerchantOrderReference(merchantId, " ORD-1001 ")).isPresent();
    }

    @Test
    void blankMerchantOrderReferenceFindsNothing() {
        assertThat(parcelService.findByMerchantOrderReference(merchantId, "  ")).isEmpty();
        verifyNoInteractions(parcelRepository);
    }

    @Test
    void listsParcelsWithAStatusNewestFirstWhenNoSortIsGiven() {
        Pageable expected = PageRequest.of(0, 20, ParcelService.DEFAULT_SORT);
        when(parcelRepository.findByCurrentStatusStatusCode("CREATED", expected))
                .thenReturn(new PageImpl<>(List.of(savedParcel("SD7KQ2MX9TRB4H")), expected, 1));

        assertThat(parcelService.listByStatus(ParcelStatusCode.CREATED, PageRequest.of(0, 20))).hasSize(1);
    }

    @Test
    void countsEveryStatusIncludingThoseWithNoParcels() {
        when(parcelRepository.countByStatus()).thenReturn(List.of(
                new StatusCount("CREATED", 4L), new StatusCount("DELIVERED", 7L)));

        Map<ParcelStatusCode, Long> counts = parcelService.countByStatus();

        assertThat(counts).containsExactly(
                entry(ParcelStatusCode.CREATED, 4L), entry(ParcelStatusCode.PICKED_UP, 0L),
                entry(ParcelStatusCode.IN_TRANSIT, 0L), entry(ParcelStatusCode.OUT_FOR_DELIVERY, 0L),
                entry(ParcelStatusCode.DELIVERED, 7L), entry(ParcelStatusCode.FAILED_ATTEMPT, 0L),
                entry(ParcelStatusCode.FAILED, 0L));
    }

    @Test
    void countsOnlyTheMerchantsOrders() {
        when(parcelRepository.countByStatusForMerchant(merchantId)).thenReturn(List.of(new StatusCount("FAILED", 2L)));

        assertThat(parcelService.countByStatusForMerchant(merchantId))
                .containsEntry(ParcelStatusCode.FAILED, 2L)
                .containsEntry(ParcelStatusCode.CREATED, 0L);
    }

    @Test
    void getParcelThrowsNotFoundForAnUnknownId() {
        UUID unknown = UUID.randomUUID();
        when(parcelRepository.findWithDetailsById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parcelService.getParcel(unknown))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Parcel not found");
    }

    @Test
    void findByTrackingNumberIgnoresCaseAndSpaces() {
        when(parcelRepository.findByTrackingNumber("SD7KQ2MX9TRB4H")).thenReturn(Optional.of(savedParcel("SD7KQ2MX9TRB4H")));

        Optional<ParcelDetails> found = parcelService.findByTrackingNumber("  sd7kq2mx9trb4h ");

        assertThat(found).get().extracting(ParcelDetails::currentStatus).isEqualTo(ParcelStatusCode.IN_TRANSIT);
    }

    @Test
    void findByTrackingNumberIsEmptyForAnUnknownNumber() {
        when(parcelRepository.findByTrackingNumber(anyString())).thenReturn(Optional.empty());

        assertThat(parcelService.findByTrackingNumber("SD0000")).isEmpty();
    }

    @Test
    void listsCustomerParcelsNewestFirstWhenNoSortIsGiven() {
        Pageable expected = PageRequest.of(1, 5, ParcelService.DEFAULT_SORT);
        when(parcelRepository.findByCustomerUserId(customerId, expected))
                .thenReturn(new PageImpl<>(List.of(savedParcel("SD7KQ2MX9TRB4H")), expected, 6));

        Page<ParcelDetails> page = parcelService.listByCustomer(customerId, PageRequest.of(1, 5));

        assertThat(page.getContent()).extracting(ParcelDetails::trackingNumber).containsExactly("SD7KQ2MX9TRB4H");
        assertThat(page.getTotalElements()).isEqualTo(6);
    }

    @Test
    void keepsTheCallersSortWhenListingMerchantParcels() {
        Pageable bySize = PageRequest.of(0, 20, Sort.by("weightKg"));
        when(parcelRepository.findByMerchantId(merchantId, bySize)).thenReturn(Page.empty(bySize));

        assertThat(parcelService.listByMerchant(merchantId, bySize)).isEmpty();
    }
}
