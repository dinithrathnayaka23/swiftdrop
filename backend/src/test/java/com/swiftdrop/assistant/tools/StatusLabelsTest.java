package com.swiftdrop.assistant.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.swiftdrop.parcel.model.ParcelStatusCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StatusLabelsTest {

    @ParameterizedTest
    @EnumSource(ParcelStatusCode.class)
    void everyStatusHasPlainWords(ParcelStatusCode status) {
        assertThat(StatusLabels.label(status)).isNotBlank();
        assertThat(StatusLabels.description(status)).isNotBlank();
    }

    @Test
    void aFailedAttemptIsNotFinal() {
        assertThat(StatusLabels.label(ParcelStatusCode.FAILED_ATTEMPT)).isEqualTo("Delivery attempt failed");
        assertThat(StatusLabels.description(ParcelStatusCode.FAILED_ATTEMPT))
                .contains("try again").contains("contact you");
    }
}
