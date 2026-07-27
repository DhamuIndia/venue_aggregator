package com.staminal.venue.notifications.whatsapp;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WhatsAppWebhookProcessor {

    private static final String WHATSAPP_OBJECT = "whatsapp_business_account";

    private final ObjectMapper objectMapper;
    private final WhatsAppCloudApiProperties properties;
    private final WhatsAppFailureClassifier failureClassifier;
    private final WhatsAppDeliveryStateService deliveryStateService;

    public WhatsAppWebhookProcessingResult process(byte[] payload) throws IOException {
        MetaWhatsAppWebhookPayload webhook = objectMapper.readValue(
                payload,
                MetaWhatsAppWebhookPayload.class);
        if (!WHATSAPP_OBJECT.equals(webhook.object())) {
            return new WhatsAppWebhookProcessingResult(0, 0, 0);
        }

        int received = 0;
        int matched = 0;
        int ignored = 0;
        for (MetaWhatsAppWebhookPayload.Entry entry : safe(webhook.entry())) {
            for (MetaWhatsAppWebhookPayload.Change change : safe(entry.changes())) {
                if (!"messages".equals(change.field())
                        || change.value() == null
                        || !isConfiguredPhoneNumber(change.value().metadata())) {
                    continue;
                }
                for (MetaWhatsAppWebhookPayload.Status providerStatus
                        : safe(change.value().statuses())) {
                    received++;
                    MetaMessageDeliveryStatus status = MetaMessageDeliveryStatus
                            .fromProviderValue(providerStatus.status())
                            .orElse(null);
                    if (status == null
                            || providerStatus.id() == null
                            || providerStatus.id().isBlank()) {
                        ignored++;
                        continue;
                    }
                    boolean applied = deliveryStateService.apply(
                            providerStatus.id().trim(),
                            status,
                            timestamp(providerStatus.timestamp()),
                            failure(providerStatus.errors()));
                    if (applied) {
                        matched++;
                    } else {
                        ignored++;
                    }
                }
            }
        }
        return new WhatsAppWebhookProcessingResult(received, matched, ignored);
    }

    private boolean isConfiguredPhoneNumber(MetaWhatsAppWebhookPayload.Metadata metadata) {
        String configuredPhoneNumberId = properties.getPhoneNumberId();
        return configuredPhoneNumberId == null
                || configuredPhoneNumberId.isBlank()
                || (metadata != null
                    && Objects.equals(configuredPhoneNumberId.trim(), metadata.phoneNumberId()));
    }

    private MetaWebhookFailure failure(List<MetaWhatsAppWebhookPayload.Error> errors) {
        MetaWhatsAppWebhookPayload.Error error = safe(errors)
                .stream()
                .findFirst()
                .orElse(null);
        if (error == null) {
            return null;
        }
        String detail = error.errorData() == null ? null : error.errorData().details();
        return new MetaWebhookFailure(
                error.code(),
                firstNonBlank(error.title(), "Meta delivery failure"),
                firstNonBlank(detail, error.message(), error.title(), "Meta delivery failed"),
                failureClassifier.isTemporary(error.code(), error.transientFailure()));
    }

    private Instant timestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(value.trim()));
        } catch (NumberFormatException exception) {
            try {
                return Instant.parse(value.trim());
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
