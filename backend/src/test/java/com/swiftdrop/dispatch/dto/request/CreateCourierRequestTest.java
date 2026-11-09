package com.swiftdrop.dispatch.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CreateCourierRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static CreateCourierRequest request(String name, String email, String phone, String password,
                                                String vehicleType, String registration) {
        return new CreateCourierRequest(name, email, phone, password, vehicleType, registration, null);
    }

    private static Set<String> invalidFields(CreateCourierRequest request) {
        return VALIDATOR.validate(request).stream().map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void acceptsAValidCourier() {
        assertThat(VALIDATOR.validate(request("Ramesh Perera", "ramesh@example.com", "+94771234567", "courier2026",
                "Motorbike", "WP CAB-1234"))).isEmpty();
    }

    @Test
    void flagsEveryInvalidField() {
        CreateCourierRequest bad = request("R2", "not-an-email", "0771234567", "short", " ", "AB@12");

        assertThat(invalidFields(bad)).containsExactlyInAnyOrder("fullName", "email", "phone", "password",
                "vehicleType", "vehicleRegistration");
    }

    @Test
    void everyFieldIsRequired() {
        assertThat(invalidFields(request(null, null, null, null, null, null))).containsExactlyInAnyOrder("fullName",
                "email", "phone", "password", "vehicleType", "vehicleRegistration");
    }

    @Test
    void aPasswordNeedsALetterAndANumber() {
        assertThat(invalidFields(request("Ramesh Perera", "ramesh@example.com", "+94771234567", "onlyletters",
                "Van", "ABC-1"))).containsExactly("password");
    }

    @Test
    void limitsTheServiceAreas() {
        Set<UUID> tooMany = java.util.stream.IntStream.range(0, 51).mapToObj(i -> UUID.randomUUID())
                .collect(Collectors.toSet());
        CreateCourierRequest request = new CreateCourierRequest("Ramesh Perera", "ramesh@example.com", "+94771234567",
                "courier2026", "Van", "ABC-1", tooMany);

        Set<ConstraintViolation<CreateCourierRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("serviceAreaIds");
    }

    @Test
    void theToStringHidesThePassword() {
        assertThat(request("Ramesh Perera", "ramesh@example.com", "+94771234567", "courier2026", "Van", "ABC-1")
                .toString()).doesNotContain("courier2026").doesNotContain("ramesh");
    }

    @Test
    void anUpdateMustNotBlankTheVehicle() {
        Set<String> blank = VALIDATOR.validate(new UpdateCourierRequest("  ", "AB@1", null)).stream()
                .map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());

        assertThat(blank).containsExactlyInAnyOrder("vehicleType", "vehicleRegistration");
        assertThat(VALIDATOR.validate(new UpdateCourierRequest(null, null, null))).isEmpty();
    }
}
