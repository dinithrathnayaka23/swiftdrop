package com.swiftdrop.booking.controller;

import com.swiftdrop.booking.dto.request.BookingRequest;
import com.swiftdrop.booking.dto.response.BookingConfirmationResponse;
import com.swiftdrop.booking.dto.response.FeeEstimateResponse;
import com.swiftdrop.booking.service.BookingService;
import com.swiftdrop.common.api.ApiPaths;
import com.swiftdrop.common.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Delivery booking for customers: the Book a Delivery screens. */
@RestController
@RequestMapping(ApiPaths.API_V1 + "/bookings")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /** The fee for a booking, shown before the customer confirms. Nothing is saved. */
    @PostMapping("/estimate")
    public FeeEstimateResponse estimate(@Valid @RequestBody BookingRequest request) {
        return bookingService.estimate(request);
    }

    /** Confirms the booking for the signed-in customer. */
    @PostMapping
    public ResponseEntity<BookingConfirmationResponse> book(
            @Valid @RequestBody BookingRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        BookingConfirmationResponse confirmation = bookingService.book(request, UUID.fromString(user.userId()));
        return ResponseEntity.created(URI.create(ApiPaths.API_V1 + "/customer/parcels/by-tracking-number/"
                        + confirmation.trackingNumber()))
                .body(confirmation);
    }
}