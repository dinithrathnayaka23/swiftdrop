package com.swiftdrop.parcel.model;

import static com.swiftdrop.parcel.model.ParcelStatusCode.CREATED;
import static com.swiftdrop.parcel.model.ParcelStatusCode.DELIVERED;
import static com.swiftdrop.parcel.model.ParcelStatusCode.FAILED;
import static com.swiftdrop.parcel.model.ParcelStatusCode.FAILED_ATTEMPT;
import static com.swiftdrop.parcel.model.ParcelStatusCode.IN_TRANSIT;
import static com.swiftdrop.parcel.model.ParcelStatusCode.OUT_FOR_DELIVERY;
import static com.swiftdrop.parcel.model.ParcelStatusCode.PICKED_UP;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The allowed changes from the parcel status diagram (report section 3.6, Figure 8, with V6_003). */
class ParcelStatusCodeTest {

    /** Every allowed change; every other pair of statuses must be refused. */
    private static final Set<String> ALLOWED = Set.of(
            // forward one step at a time
            "CREATED>PICKED_UP",
            "PICKED_UP>IN_TRANSIT",
            "IN_TRANSIT>OUT_FOR_DELIVERY",
            "OUT_FOR_DELIVERY>DELIVERED",
            // the courier reports a failed attempt after pickup
            "PICKED_UP>FAILED_ATTEMPT",
            "IN_TRANSIT>FAILED_ATTEMPT",
            "OUT_FOR_DELIVERY>FAILED_ATTEMPT",
            // the dispatcher retries or closes
            "FAILED_ATTEMPT>CREATED",
            "FAILED_ATTEMPT>FAILED");

    /** All 7 x 7 pairs. */
    static Stream<Arguments> everyPair() {
        return Arrays.stream(ParcelStatusCode.values()).flatMap(from -> Arrays.stream(ParcelStatusCode.values())
                .map(to -> Arguments.of(from, to, ALLOWED.contains(from + ">" + to))));
    }

    @ParameterizedTest(name = "{0} -> {1} allowed: {2}")
    @MethodSource("everyPair")
    void allowsOnlyTheChangesInTheStatusDiagram(ParcelStatusCode from, ParcelStatusCode to, boolean allowed) {
        assertThat(from.canChangeTo(to)).isEqualTo(allowed);
    }

    @Test
    void theDirectFailureArrowsAreGone() {
        assertThat(PICKED_UP.canChangeTo(FAILED)).isFalse();
        assertThat(IN_TRANSIT.canChangeTo(FAILED)).isFalse();
        assertThat(OUT_FOR_DELIVERY.canChangeTo(FAILED)).isFalse();
    }

    @Test
    void deliveredAndFailedAreTheOnlyEndStates() {
        assertThat(DELIVERED.isTerminal()).isTrue();
        assertThat(FAILED.isTerminal()).isTrue();
        assertThat(FAILED_ATTEMPT.isTerminal()).isFalse();
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
