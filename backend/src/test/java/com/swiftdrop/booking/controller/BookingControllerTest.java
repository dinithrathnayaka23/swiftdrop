package com.swiftdrop.booking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swiftdrop.booking.dto.request.BookingAddressRequest;
import com.swiftdrop.booking.dto.request.BookingRequest;
import com.swiftdrop.booking.dto.response.BookingConfirmationResponse;
import com.swiftdrop.booking.service.BookingService;
import com.swiftdrop.common.security.AuthenticatedUser;
import com.swiftdrop.common.security.Role;
import com.swiftdrop.parcel.model.ParcelStatusCode;
import com.swiftdrop.parcel.model.ParcelType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    private final UUID customerId = UUID.randomUUID();
    private final AuthenticatedUser customer = new AuthenticatedUser(customerId.toString(), Set.of(Role.CUSTOMER));

    private BookingRequest request() {
        BookingAddressRequest address = new BookingAddressRequest(
                "Kamala Silva", "0771234567", "12 Main Street", null, "Kandy", "Kandy", null);
        return new BookingRequest(
                address, address, ParcelType.PARCEL, new BigDecimal("2"), null, null, null, null, null);
    }

    @Test
    void onlyCustomersCanBook() {
        assertThat(BookingController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('CUSTOMER')");
    }

    @Test
    void bookUsesTheSignedInCustomerAndReturns201WithLocation() {
        BookingRequest request = request();
        when(bookingService.book(request, customerId)).thenReturn(new BookingConfirmationResponse(
                UUID.randomUUID(), "SD7KQ2MX9TRB4H", ParcelStatusCode.CREATED,
                new BigDecimal("500.00"), "LKR", Instant.now()));

        ResponseEntity<BookingConfirmationResponse> result =
                new BookingController(bookingService).book(request, customer);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getHeaders().getLocation().toString())
                .endsWith("/customer/parcels/by-tracking-number/SD7KQ2MX9TRB4H");
        assertThat(result.getBody().trackingNumber()).isEqualTo("SD7KQ2MX9TRB4H");
    }

    @Test
    void estimateCallsTheService() {
        BookingRequest request = request();

        new BookingController(bookingService).estimate(request);

        verify(bookingService).estimate(request);
    }
}

/*checks the controller is customer-only,
 that booking uses the signed-in customer and 
 returns 201 with a Location header, and that
  estimate calls the service. */