package com.staminal.venue.leads;

import java.security.SecureRandom;
import java.util.regex.Pattern;

public final class LeadReference {

    private static final String PREFIX = "LEAD-";
    private static final char[] ALPHABET = "0123456789ABCDEF".toCharArray();
    private static final int TOKEN_LENGTH = 20;
    private static final Pattern FORMAT = Pattern.compile("^LEAD-[0-9A-F]{20}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private LeadReference() {
    }

    public static String create() {
        StringBuilder reference = new StringBuilder(PREFIX);
        for (int index = 0; index < TOKEN_LENGTH; index++) {
            reference.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return reference.toString();
    }

    public static boolean isValid(String reference) {
        return reference != null && FORMAT.matcher(reference).matches();
    }
}
