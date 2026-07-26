package com.staminal.venue.notifications.whatsapp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staminal.venue.leads.LeadReference;

@Component
public class MetaWhatsAppCloudApiClient implements WhatsAppCloudApiClient {

    private final WhatsAppCloudApiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final WhatsAppFailureClassifier failureClassifier;

    public MetaWhatsAppCloudApiClient(
            WhatsAppCloudApiProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            WhatsAppFailureClassifier failureClassifier) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.failureClassifier = failureClassifier;
        this.restClient = restClientBuilder
                .baseUrl(trimTrailingSlash(properties.getGraphApiBaseUrl()))
                .build();
    }

    @Override
    public WhatsAppCloudApiSendResult sendTemplate(WhatsAppTemplateMessage message) {
        properties.validateForSending();
        MetaTemplateRequest request = toRequest(message);

        try {
            MetaSendResponse response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment(
                                    normalizedVersion(properties.getGraphApiVersion()),
                                    properties.getPhoneNumberId().trim(),
                                    "messages")
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken().trim())
                    .body(request)
                    .retrieve()
                    .body(MetaSendResponse.class);

            String messageId = response == null
                    || response.messages() == null
                    || response.messages().isEmpty()
                    ? null
                    : response.messages().get(0).id();
            if (messageId == null || messageId.isBlank()) {
                throw new WhatsAppCloudApiException("Meta response did not contain a message id");
            }
            return new WhatsAppCloudApiSendResult(messageId.trim());
        } catch (WhatsAppCloudApiException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw providerException(exception);
        } catch (RestClientException exception) {
            throw new WhatsAppCloudApiException(
                    null,
                    "Submission result unknown",
                    "Meta WhatsApp submission result is unknown; automatic retry is disabled",
                    false,
                    false,
                    exception);
        }
    }

    private WhatsAppCloudApiException providerException(RestClientResponseException exception) {
        try {
            MetaErrorResponse response = objectMapper.readValue(
                    exception.getResponseBodyAsByteArray(),
                    MetaErrorResponse.class);
            MetaError error = response.error();
            if (error != null) {
                String detail = error.errorData() == null
                        ? null
                        : error.errorData().details();
                String reason = firstNonBlank(detail, error.message(), "Meta rejected the message");
                boolean temporary = failureClassifier.isTemporary(
                        error.code(),
                        error.transientFailure());
                return new WhatsAppCloudApiException(
                        error.code(),
                        firstNonBlank(error.type(), "Meta API error"),
                        reason,
                        temporary,
                        true,
                        exception);
            }
        } catch (IOException ignored) {
            // The response was not a Graph API error object. Treat it as ambiguous.
        }
        return new WhatsAppCloudApiException(
                null,
                "Submission result unknown",
                "Meta WhatsApp submission failed without a structured error; automatic retry is disabled",
                false,
                false,
                exception);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private MetaTemplateRequest toRequest(WhatsAppTemplateMessage message) {
        List<MetaParameter> parameters = message.bodyParameters()
                .stream()
                .map(value -> new MetaParameter("text", value))
                .toList();
        String dynamicUrlSuffix = required(
                message.dynamicUrlSuffix(),
                "WhatsApp dynamic URL suffix");
        if (!LeadReference.isValid(dynamicUrlSuffix)) {
            throw new WhatsAppCloudApiException(
                    "WhatsApp dynamic URL suffix must be an opaque lead reference");
        }
        List<MetaComponent> components = new ArrayList<>();
        components.add(new MetaComponent("body", null, null, parameters));
        components.add(new MetaComponent(
                "button",
                "url",
                String.valueOf(properties.getLeadTemplateUrlButtonIndex()),
                List.of(new MetaParameter("text", dynamicUrlSuffix))));
        return new MetaTemplateRequest(
                "whatsapp",
                "individual",
                destinationDigits(message.destination()),
                "template",
                new MetaTemplate(
                        required(message.templateName(), "Template name"),
                        new MetaLanguage(required(message.languageCode(), "Template language")),
                        List.copyOf(components)));
    }

    private String destinationDigits(String destination) {
        String value = required(destination, "WhatsApp destination")
                .replaceAll("[\\s+()\\-]", "");
        if (!value.matches("^[1-9]\\d{7,14}$")) {
            throw new WhatsAppCloudApiException("WhatsApp destination must be in international format");
        }
        return value;
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new WhatsAppCloudApiException(label + " is required");
        }
        return value.trim();
    }

    private String normalizedVersion(String version) {
        String normalized = version.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    record MetaTemplateRequest(
            @JsonProperty("messaging_product") String messagingProduct,
            @JsonProperty("recipient_type") String recipientType,
            String to,
            String type,
            MetaTemplate template) {
    }

    record MetaTemplate(
            String name,
            MetaLanguage language,
            List<MetaComponent> components) {
    }

    record MetaLanguage(String code) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MetaComponent(
            String type,
            @JsonProperty("sub_type") String subType,
            String index,
            List<MetaParameter> parameters) {
    }

    record MetaParameter(
            String type,
            String text) {
    }

    record MetaSendResponse(
            @JsonProperty("messaging_product") String messagingProduct,
            List<MetaMessage> messages) {
    }

    record MetaMessage(String id) {
    }

    record MetaErrorResponse(MetaError error) {
    }

    record MetaError(
            Integer code,
            String type,
            String message,
            @JsonProperty("is_transient") Boolean transientFailure,
            @JsonProperty("error_data") MetaErrorData errorData) {
    }

    record MetaErrorData(String details) {
    }
}
