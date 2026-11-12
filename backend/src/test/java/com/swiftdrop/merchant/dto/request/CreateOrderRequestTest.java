package com.swiftdrop.merchant.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.booking.dto.request.BookingAddressRequest;
import com.swiftdrop.parcel.model.ParcelType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CreateOrderRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private BookingAddressRequest address(String city) {
        return new BookingAddressRequest(
                "BrightMart", "0771234567", "12 Main Street", null, city, "Colombo", null);
    }

    private CreateOrderRequest request(String reference, BigDecimal weightKg) {
        return new CreateOrderRequest(
                reference, address("Colombo"), address("Kandy"), ParcelType.PARCEL, weightKg,
                null, null, null, null);
    }

    @Test
    void acceptsAValidOrder() {
        assertThat(validator.validate(request("ORD-1001", new BigDecimal("2.500")))).isEmpty();
    }

    @Test
    void rejectsMissingReferenceAddressesAndNonPositiveWeight() {
        var violations = validator.validate(new CreateOrderRequest(
                " ", null, null, null, BigDecimal.ZERO, null, null, null, null));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder(
                        "externalOrderReference", "pickupAddress", "dropoffAddress",
                        "parcelType", "weightKg");
    }
}
