package com.swiftdrop.pricing.api;

import java.math.BigDecimal;

/** One line of the fee breakdown, for example "Weight fee". */
public record FeeLine(String label, BigDecimal amount) {
}