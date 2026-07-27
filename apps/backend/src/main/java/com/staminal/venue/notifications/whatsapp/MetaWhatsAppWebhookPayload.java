package com.staminal.venue.notifications.whatsapp;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record MetaWhatsAppWebhookPayload(
        String object,
        List<Entry> entry) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(List<Change> changes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Change(String field, Value value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Value(
            Metadata metadata,
            List<Status> statuses) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Metadata(
            @JsonProperty("phone_number_id") String phoneNumberId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Status(
            String id,
            String status,
            String timestamp,
            @JsonProperty("recipient_id") String recipientId,
            List<Error> errors) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Error(
            Integer code,
            String title,
            String message,
            @JsonProperty("is_transient") Boolean transientFailure,
            @JsonProperty("error_data") ErrorData errorData) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorData(String details) {
    }
}
