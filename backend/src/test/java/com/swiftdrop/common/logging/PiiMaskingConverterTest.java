package com.swiftdrop.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiMaskingConverterTest {

    @Test
    void masksEmailsPhonesAndTokens() {
        String masked = PiiMaskingConverter.mask(
                "user jane.doe@example.com phone 0771234567 / +94771234567 auth Bearer abc.def.ghi");

        assertThat(masked)
                .doesNotContain("jane.doe@example.com", "0771234567", "+94771234567", "abc.def.ghi")
                .contains("***@***", "***phone***", "Bearer ***");
    }

    @Test
    void leavesIdsAndUuidsAlone() {
        String message = "parcel 550e8400-e29b-41d4-a716-446655440000 assigned to courier 17";

        assertThat(PiiMaskingConverter.mask(message)).isEqualTo(message);
    }
}
