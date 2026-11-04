package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.api.FeeApi;
import com.swiftdrop.pricing.api.FeeEstimate;
import com.swiftdrop.pricing.api.FeeLine;
import com.swiftdrop.pricing.api.FeeRequest;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Temporary implementation so other members can build against {@link FeeApi}.
 * It is replaced by the real fee calculation (and then deleted).
 */
@Service
public class StubFeeService implements FeeApi {

    private static final BigDecimal FIXED_FEE = new BigDecimal("500.00");

    @Override
    public FeeEstimate estimateFee(FeeRequest request) {
        return new FeeEstimate(
                FIXED_FEE,
                "LKR",
                null,
                List.of(new FeeLine("Base fee (stub)", FIXED_FEE)));
    }
}