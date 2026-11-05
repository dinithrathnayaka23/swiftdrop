package com.swiftdrop.booking.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.parcel.model.ParcelType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BookingRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private BookingAddressRequest address(String phone, String city) {
        return new BookingAddressRequest("Kamala Silva", phone, "12 Main Street", null, city, "Colombo", null);
    }

    private BookingRequest request(BookingAddressRequest pickup, String weightKg) {
        return new BookingRequest(
                pickup, address("0771234567", "Kandy"), ParcelType.PARCEL,
                new BigDecimal(weightKg), null, null, null, null, null);
    }

    @Test
    void acceptsACompleteRequest() {
        assertThat(validator.validate(request(address("0771234567", "Colombo 03"), "2.5"))).isEmpty();
    }

    @Test
    void rejectsMissingCityZeroWeightAndBadPhone() {
        var violations = validator.validate(request(address("abc", " "), "0"));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("pickup.contactPhone", "pickup.city", "weightKg");
    }
}