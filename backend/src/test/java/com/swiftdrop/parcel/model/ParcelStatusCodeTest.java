package com.swiftdrop.parcel.model;

import static com.swiftdrop.parcel.model.ParcelStatusCode.CREATED;
import static com.swiftdrop.parcel.model.ParcelStatusCode.DELIVERED;
import static com.swiftdrop.parcel.model.ParcelStatusCode.FAILED;
import static com.swiftdrop.parcel.model.ParcelStatusCode.IN_TRANSIT;
import static com.swiftdrop.parcel.model.ParcelStatusCode.OUT_FOR_DELIVERY;
import static com.swiftdrop.parcel.model.ParcelStatusCode.PICKED_UP;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The allowed changes from the report's parcel status diagram (section 3.6, Figure 8). */
class ParcelStatusCodeTest {

    @ParameterizedTest(name = "{0} -> {1} allowed: {2}")
    @CsvSource({
            // forward one step at a time
            "CREATED, PICKED_UP, true",
            "PICKED_UP, IN_TRANSIT, true",
            "IN_TRANSIT, OUT_FOR_DELIVERY, true",
            "OUT_FOR_DELIVERY, DELIVERED, true",
            // failure after pickup
            "PICKED_UP, FAILED, true",
            "IN_TRANSIT, FAILED, true",
            "OUT_FOR_DELIVERY, FAILED, true",
            // not allowed
            "CREATED, FAILED, false",
            "CREATED, IN_TRANSIT, false",
            "PICKED_UP, DELIVERED, false",
            "IN_TRANSIT, PICKED_UP, false",
            "IN_TRANSIT, IN_TRANSIT, false",
            "DELIVERED, FAILED, false",
            "FAILED, CREATED, false",
            "FAILED, PICKED_UP, false"})
    void allowsOnlyTheChangesInTheStatusDiagram(ParcelStatusCode from, ParcelStatusCode to, boolean allowed) {
        assertThat(from.canChangeTo(to)).isEqualTo(allowed);
    }

    @Test
    void deliveredAndFailedAreTheOnlyEndStates() {
        assertThat(DELIVERED.isTerminal()).isTrue();
        assertThat(FAILED.isTerminal()).isTrue();
        assertThat(CREATED.isTerminal()).isFalse();
        assertThat(PICKED_UP.isTerminal()).isFalse();
        assertThat(IN_TRANSIT.isTerminal()).isFalse();
        assertThat(OUT_FOR_DELIVERY.isTerminal()).isFalse();
    }

    @Test
    void nextStatusesCannotBeChangedByCallers() {
        assertThat(CREATED.nextStatuses()).isUnmodifiable();
    }
}
