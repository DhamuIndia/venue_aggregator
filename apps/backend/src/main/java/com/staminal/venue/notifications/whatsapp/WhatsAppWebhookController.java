package com.staminal.venue.notifications.whatsapp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/integrations/meta/whatsapp/webhook")
public class WhatsAppWebhookController {

    private final WhatsAppCloudApiProperties properties;
    private final WhatsAppWebhookSignatureVerifier signatureVerifier;
    private final WhatsAppWebhookProcessor webhookProcessor;

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!webhookReady()
                || !"subscribe".equals(mode)
                || challenge == null
                || !secureEquals(properties.getWebhookVerifyToken(), verifyToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(challenge);
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> receive(
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] payload) {
        if (!webhookReady()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!signatureVerifier.isValid(payload, signature, properties.getAppSecret())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            webhookProcessor.process(payload);
            return ResponseEntity.ok("EVENT_RECEIVED");
        } catch (IOException exception) {
            return ResponseEntity.badRequest().body("INVALID_PAYLOAD");
        }
    }

    private boolean webhookReady() {
        if (!properties.isWebhookEnabled()) {
            return false;
        }
        try {
            properties.validateWebhookConfiguration();
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private boolean secureEquals(String expected, String supplied) {
        if (expected == null || supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }
}
