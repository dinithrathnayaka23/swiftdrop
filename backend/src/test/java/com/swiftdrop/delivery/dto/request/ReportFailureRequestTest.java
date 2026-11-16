package com.swiftdrop.delivery.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReportFailureRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> invalidFields(Object request) {
        return VALIDATOR.validate(request).stream().map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void needsAReasonAndTakesAnOptionalNoteOfAtMost500Characters() {
        assertThat(invalidFields(new ReportFailureRequest("REFUSED", null))).isEmpty();
        assertThat(invalidFields(new ReportFailureRequest("OTHER", "x".repeat(500)))).isEmpty();
        assertThat(invalidFields(new ReportFailureRequest("OTHER", "x".repeat(501)))).containsExactly("note");
        assertThat(invalidFields(new ReportFailureRequest(" ", null))).containsExactly("reasonCode");
        assertThat(invalidFields(new ReportFailureRequest(null, null))).containsExactly("reasonCode");
        assertThat(invalidFields(new ReportFailureRequest("X".repeat(41), null))).containsExactly("reasonCode");
    }
}
