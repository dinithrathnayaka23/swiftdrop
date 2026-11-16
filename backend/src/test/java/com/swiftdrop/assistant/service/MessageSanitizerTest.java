package com.swiftdrop.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MessageSanitizerTest {

    private final MessageSanitizer sanitizer = new MessageSanitizer();

    @Test
    void hidesEmailsAndSriLankanAndInternationalPhoneNumbers() {
        String text = sanitizer.sanitize("Mail ayesha.k@gmail.com or call 0771234567, 077-123 4567, "
                + "+94 77 123 4567, 94771234567, 011 234 5678 or +44 20 7946 0958.");

        assertThat(text).doesNotContain("ayesha", "0771234567", "077-123 4567", "+94 77", "94771234567",
                "011 234 5678", "+44");
        assertThat(text).contains(MessageSanitizer.EMAIL_HIDDEN).contains(MessageSanitizer.PHONE_HIDDEN);
    }

    @Test
    void keepsTrackingNumbersWeightsAndPrices() {
        String text = "Where is SD7KQ2MX9TRB4H? It weighs 2.5 kg and cost LKR 1250.00 on 2026-11-12.";

        assertThat(sanitizer.sanitize(text)).isEqualTo(text);
    }
}
