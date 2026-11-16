package com.swiftdrop.dispatch.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AssignmentRequestsTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final UUID ID = UUID.randomUUID();

    private static Set<String> invalidFields(Object request) {
        return VALIDATOR.validate(request).stream().map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static List<UUID> ids(int count) {
        return Stream.generate(UUID::randomUUID).limit(count).toList();
    }

    @Test
    void assignNeedsAParcelAndACourier() {
        assertThat(invalidFields(new AssignParcelRequest(ID, ID))).isEmpty();
        assertThat(invalidFields(new AssignParcelRequest(null, null))).containsExactlyInAnyOrder("parcelId",
                "courierId");
    }

    @Test
    void bulkTakesOneToFiftyParcels() {
        assertThat(invalidFields(new BulkAssignRequest(ID, ids(1)))).isEmpty();
        assertThat(invalidFields(new BulkAssignRequest(ID, ids(50)))).isEmpty();
        assertThat(invalidFields(new BulkAssignRequest(ID, ids(51)))).containsExactly("parcelIds");
        assertThat(invalidFields(new BulkAssignRequest(ID, List.of()))).containsExactly("parcelIds");
        assertThat(invalidFields(new BulkAssignRequest(null, null))).containsExactlyInAnyOrder("courierId",
                "parcelIds");
    }

    @Test
    void bulkRefusesAMissingParcelId() {
        List<UUID> withGap = new ArrayList<>(Arrays.asList(ID, null));

        assertThat(invalidFields(new BulkAssignRequest(ID, withGap))).containsExactly("parcelIds[1].<list element>");
    }

    @Test
    void reassignNeedsACourierAndAReasonOfAtMost500Characters() {
        assertThat(invalidFields(new ReassignParcelRequest(ID, "Vehicle broke down"))).isEmpty();
        assertThat(invalidFields(new ReassignParcelRequest(ID, "x".repeat(500)))).isEmpty();
        assertThat(invalidFields(new ReassignParcelRequest(ID, "x".repeat(501)))).containsExactly("reason");
        assertThat(invalidFields(new ReassignParcelRequest(null, "  "))).containsExactlyInAnyOrder("courierId",
                "reason");
    }

    @Test
    void unassignReasonIsOptionalButLimited() {
        assertThat(invalidFields(new UnassignParcelRequest(null))).isEmpty();
        assertThat(invalidFields(new UnassignParcelRequest(String.join("", Collections.nCopies(501, "x")))))
                .containsExactly("reason");
    }
}
