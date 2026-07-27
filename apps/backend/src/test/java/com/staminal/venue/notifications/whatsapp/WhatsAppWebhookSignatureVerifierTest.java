package com.staminal.venue.notifications.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class WhatsAppWebhookSignatureVerifierTest {

    private final WhatsAppWebhookSignatureVerifier verifier =
            new WhatsAppWebhookSignatureVerifier();

    @Test
    void validatesSha256SignatureAgainstTheExactRawPayload() {
        byte[] payload = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String signature =
                "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";

        assertThat(verifier.isValid(
                payload,
                signature,
                "It's a Secret to Everybody"))
                .isTrue();
        assertThat(verifier.isValid(
                "Hello, World?".getBytes(StandardCharsets.UTF_8),
                signature,
                "It's a Secret to Everybody"))
                .isFalse();
    }

    @Test
    void rejectsMissingMalformedAndWrongSignatures() {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(payload, null, "secret")).isFalse();
        assertThat(verifier.isValid(payload, "sha256=not-hex", "secret")).isFalse();
        assertThat(verifier.isValid(payload, "sha256=00", "secret")).isFalse();
    }
}
