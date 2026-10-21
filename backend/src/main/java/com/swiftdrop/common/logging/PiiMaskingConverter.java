package com.swiftdrop.common.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Pattern;

/**
 * Logback converter that masks common personal data in log messages
 * (used as {@code %maskedMsg} in logback-spring.xml).
 *
 * <p>This is a safety net, not a licence to log personal data. The rule is still:
 * never log names, emails, phone numbers, addresses, passwords or tokens; log IDs instead.
 */
public class PiiMaskingConverter extends MessageConverter {

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    // Sri Lankan mobiles (07XXXXXXXX / +947XXXXXXXX) and any +country-code number.
    private static final Pattern PHONE =
            Pattern.compile("(?<![\\w-])(?:\\+94|0)7\\d{8}(?!\\d)|\\+\\d[\\d -]{7,}\\d");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("eyJ[\\w-]+\\.[\\w-]+\\.[\\w-]+");

    @Override
    public String convert(ILoggingEvent event) {
        return mask(super.convert(event));
    }

    public static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String masked = BEARER.matcher(message).replaceAll("Bearer ***");
        masked = JWT.matcher(masked).replaceAll("***jwt***");
        masked = EMAIL.matcher(masked).replaceAll("***@***");
        return PHONE.matcher(masked).replaceAll("***phone***");
    }
}
