package com.swiftdrop.delivery.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class UpdateDeliveryStatusRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> invalidFields(Object request) {
        return VALIDATOR.validate(request).stream().map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void needsAStatusAndTakesAnOptionalNoteOfAtMost500Characters() {
        assertThat(invalidFields(new UpdateDeliveryStatusRequest(ParcelStatusCode.IN_TRANSIT, null))).isEmpty();
        assertThat(invalidFields(new UpdateDeliveryStatusRequest(ParcelStatusCode.IN_TRANSIT, "x".repeat(500))))
                .isEmpty();
        assertThat(invalidFields(new UpdateDeliveryStatusRequest(ParcelStatusCode.IN_TRANSIT, "x".repeat(501))))
                .containsExactly("note");
        assertThat(invalidFields(new UpdateDeliveryStatusRequest(null, null))).containsExactly("status");
    }
}
