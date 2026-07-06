package com.staminal.venue.enquiries;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class EnquiryIds {

    private EnquiryIds() {
    }

    public static Long parse(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.toUpperCase().startsWith("ENQ-")) {
            normalized = normalized.substring(4);
        }

        try {
            return Long.valueOf(normalized.trim());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid enquiry id", exception);
        }
    }

    public static String format(Long id) {
        if (id == null) {
            return null;
        }
        return "ENQ-" + String.format("%06d", id);
    }
}
