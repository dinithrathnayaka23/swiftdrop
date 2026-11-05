package com.swiftdrop.booking.dto.response;

import com.swiftdrop.pricing.api.FeeLine;
import java.math.BigDecimal;
import java.util.List;

/** The estimated fee for a booking, with its breakdown. */
public record FeeEstimateResponse(BigDecimal totalFee, String currency, List<FeeLine> lines) {
}

/*what the review screen shows: the total and the lines that 
make it up (base fee, extra weight and so on). It
 leaves out the pricing rule id, which the customer doesn’t need. */