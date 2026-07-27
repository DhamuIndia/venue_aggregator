package com.staminal.venue.notifications.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

@Component
public class WhatsAppWebhookSignatureVerifier {

    private static final String PREFIX = "sha256=";

    public boolean isValid(byte[] payload, String signatureHeader, String appSecret) {
        if (payload == null
                || signatureHeader == null
                || !signatureHeader.startsWith(PREFIX)
                || appSecret == null
                || appSecret.isBlank()) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    appSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] expected = mac.doFinal(payload);
            byte[] supplied = HexFormat.of().parseHex(signatureHeader.substring(PREFIX.length()));
            return MessageDigest.isEqual(expected, supplied);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return false;
        }
    }
}
