package com.staminal.venue.notifications.whatsapp;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class WhatsAppFailureClassifier {

    private static final Set<Integer> KNOWN_TEMPORARY_CODES = Set.of(
            1,
            2,
            4,
            17,
            32,
            341,
            130429,
            131000,
            131016,
            131048,
            131057);

    public boolean isTemporary(Integer code, Boolean providerTransient) {
        if (providerTransient != null) {
            return providerTransient;
        }
        return code != null && KNOWN_TEMPORARY_CODES.contains(code);
    }
}
