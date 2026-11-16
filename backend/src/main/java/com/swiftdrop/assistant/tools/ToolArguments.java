package com.swiftdrop.assistant.tools;

import java.math.BigDecimal;
import tools.jackson.databind.JsonNode;

/** Typed, checked access to the model's arguments for one call. */
public final class ToolArguments {

    private final JsonNode values;

    ToolArguments(JsonNode values) {
        this.values = values;
    }

    /** A required text argument, trimmed, at most {@code maxLength} characters. */
    public String text(String name, int maxLength) {
        JsonNode value = values.get(name);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new InvalidToolArgumentException(name, "is required");
        }
        String text = value.asString().trim();
        if (text.length() > maxLength) {
            throw new InvalidToolArgumentException(name, "is too long");
        }
        return text;
    }

    /** A required number greater than 0. Numbers written as text ("2.5") are accepted too. */
    public BigDecimal positiveNumber(String name) {
        BigDecimal number = optionalPositiveNumber(name);
        if (number == null) {
            throw new InvalidToolArgumentException(name, "is required");
        }
        return number;
    }

    /** An optional number greater than 0; {@code null} when absent or null. */
    public BigDecimal optionalPositiveNumber(String name) {
        JsonNode value = values.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        BigDecimal number;
        try {
            number = value.isNumber() ? value.decimalValue()
                    : value.isString() ? new BigDecimal(value.asString().trim()) : null;
        } catch (NumberFormatException ex) {
            number = null;
        }
        if (number == null || number.signum() <= 0) {
            throw new InvalidToolArgumentException(name, "must be a number greater than 0");
        }
        return number;
    }
}
