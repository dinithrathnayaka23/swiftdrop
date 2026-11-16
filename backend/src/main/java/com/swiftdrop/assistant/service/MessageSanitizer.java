package com.swiftdrop.assistant.service;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Hides emails and phone numbers before any text is sent to the model provider. */
@Component
public class MessageSanitizer {

    static final String EMAIL_HIDDEN = "[email hidden]";
    static final String PHONE_HIDDEN = "[phone hidden]";

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    // Sri Lankan numbers (077 123 4567, 011 234 5678, +94 / 94 ...) and any other +country-code number.
    // The \w guards keep tracking numbers such as SD7KQ2MX9TRB4H intact.
    private static final Pattern SRI_LANKAN_PHONE =
            Pattern.compile("(?<![\\w+])(?:\\+?94|0)[\\s-]?\\d{2}[\\s-]?\\d{3}[\\s-]?\\d{4}(?!\\w)");
    private static final Pattern INTERNATIONAL_PHONE = Pattern.compile("(?<!\\w)\\+\\d[\\d\\s()-]{6,}\\d(?!\\w)");

    public String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String hidden = EMAIL.matcher(text).replaceAll(EMAIL_HIDDEN);
        hidden = SRI_LANKAN_PHONE.matcher(hidden).replaceAll(PHONE_HIDDEN);
        return INTERNATIONAL_PHONE.matcher(hidden).replaceAll(PHONE_HIDDEN);
    }
}
