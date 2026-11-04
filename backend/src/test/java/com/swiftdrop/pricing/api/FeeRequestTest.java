package com.swiftdrop.pricing.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FeeRequestTest {

    private final FeeLocation colombo = new FeeLocation("Colombo", "Colombo 03", null);
    private final FeeLocation kandy = new FeeLocation("Kandy", "Kandy", "20000");

    @Test
    void acceptsRequestWithoutSize() {
        assertThatCode(() -> new FeeRequest(colombo, kandy, new BigDecimal("2.5"), null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsZeroWeight() {
        assertThatThrownBy(() -> new FeeRequest(colombo, kandy, BigDecimal.ZERO, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weightKg");
    }

    @Test
    void rejectsNegativeLength() {
        assertThatThrownBy(() -> new FeeRequest(
                colombo, kandy, BigDecimal.ONE, new BigDecimal("-1"), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lengthCm");
    }
}