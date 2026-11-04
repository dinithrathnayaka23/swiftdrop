package com.swiftdrop.parcel.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Creates tracking numbers such as {@code SD7KQ2MX9TRB4H}: "SD" and 12 random characters.
 *
 * <p>Anyone with the number can open the public tracking page, so the characters come from a
 * {@link SecureRandom} (31^12, about 2^59 possible numbers) and cannot be guessed from other
 * numbers. Look-alike characters (0/O, 1/I/L) are left out so the number is easy to read out.
 */
@Component
public class TrackingNumberGenerator {

    static final String PREFIX = "SD";
    static final int RANDOM_LENGTH = 12;
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder number = new StringBuilder(PREFIX.length() + RANDOM_LENGTH).append(PREFIX);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            number.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return number.toString();
    }
}
