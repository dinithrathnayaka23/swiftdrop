package com.swiftdrop.delivery.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DeliverRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> invalidFields(Object request) {
        return VALIDATOR.validate(request).stream().map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void needsTheRecipientsNameOfAtMost120CharactersAndANoteOfAtMost500() {
        assertThat(invalidFields(new DeliverRequest("Kasun Perera", null, null))).isEmpty();
        assertThat(invalidFields(new DeliverRequest("x".repeat(120), "y".repeat(500), null))).isEmpty();
        assertThat(invalidFields(new DeliverRequest("x".repeat(121), null, null))).containsExactly("recipientName");
        assertThat(invalidFields(new DeliverRequest("  ", null, null))).containsExactly("recipientName");
        assertThat(invalidFields(new DeliverRequest(null, "y".repeat(501), null)))
                .containsExactlyInAnyOrder("recipientName", "note");
    }

    @Test
    void anEmptyFilePartCountsAsNoPhoto() {
        assertThat(new DeliverRequest("A", null, null).hasPhoto()).isFalse();
        assertThat(new DeliverRequest("A", null, new MockMultipartFile("photo", new byte[0])).hasPhoto()).isFalse();
        assertThat(new DeliverRequest("A", null, new MockMultipartFile("photo", new byte[] {1})).hasPhoto()).isTrue();
    }
}
