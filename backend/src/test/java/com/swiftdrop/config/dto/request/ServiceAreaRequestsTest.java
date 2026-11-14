package com.swiftdrop.config.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ServiceAreaRequestsTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final CoverageAreaRequest KANDY = new CoverageAreaRequest("Kandy", "Kandy", "20000");

    private static Set<String> invalidFields(Object request) {
        return VALIDATOR.validate(request).stream().map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void nameIs2To100Characters() {
        assertThat(invalidFields(new CreateServiceAreaRequest("Ka", null, null, null))).isEmpty();
        assertThat(invalidFields(new CreateServiceAreaRequest("x".repeat(100), null, null, null))).isEmpty();
        assertThat(invalidFields(new CreateServiceAreaRequest("K", null, null, null))).containsExactly("name");
        assertThat(invalidFields(new CreateServiceAreaRequest("x".repeat(101), null, null, null)))
                .containsExactly("name");
        assertThat(invalidFields(new CreateServiceAreaRequest(" ", null, null, null))).contains("name");
    }

    @Test
    void atMost200CoverageAreasEachValid() {
        assertThat(invalidFields(new CreateServiceAreaRequest("Kandy", null, true,
                Collections.nCopies(200, KANDY)))).isEmpty();
        assertThat(invalidFields(new CreateServiceAreaRequest("Kandy", null, true,
                Collections.nCopies(201, KANDY)))).containsExactly("coverageAreas");
        assertThat(invalidFields(new CreateServiceAreaRequest("Kandy", null, true,
                List.of(new CoverageAreaRequest(" ", "Kandy", null))))).containsExactly("coverageAreas[0].district");
    }

    @Test
    void districtAndCityAreRequiredAndAtMost100() {
        assertThat(invalidFields(new CoverageAreaRequest("Kandy", "Kandy", null))).isEmpty();
        assertThat(invalidFields(new CoverageAreaRequest(null, "", null))).containsExactlyInAnyOrder("district",
                "city");
        assertThat(invalidFields(new CoverageAreaRequest("x".repeat(101), "x".repeat(101), null)))
                .containsExactlyInAnyOrder("district", "city");
    }

    @Test
    void postalCodeIsOptionalButShaped() {
        assertThat(invalidFields(new CoverageAreaRequest("Kandy", "Kandy", "20000"))).isEmpty();
        assertThat(invalidFields(new CoverageAreaRequest("Kandy", "Kandy", "CO-1 23"))).isEmpty();
        assertThat(invalidFields(new CoverageAreaRequest("Kandy", "Kandy", "12"))).containsExactly("postalCode");
        assertThat(invalidFields(new CoverageAreaRequest("Kandy", "Kandy", "2000#"))).containsExactly("postalCode");
        assertThat(invalidFields(new CoverageAreaRequest("Kandy", "Kandy", "1".repeat(16))))
                .containsExactly("postalCode");
    }

    @Test
    void updateFieldsAreOptionalButLimited() {
        assertThat(invalidFields(new UpdateServiceAreaRequest(null, null))).isEmpty();
        assertThat(invalidFields(new UpdateServiceAreaRequest("K", "x".repeat(1001))))
                .containsExactlyInAnyOrder("name", "description");
    }
}
