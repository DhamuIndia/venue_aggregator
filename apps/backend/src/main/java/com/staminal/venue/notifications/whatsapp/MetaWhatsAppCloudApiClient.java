package com.staminal.venue.notifications.whatsapp;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;

@Component
public class MetaWhatsAppCloudApiClient implements WhatsAppCloudApiClient {

    private final WhatsAppCloudApiProperties properties;
    private final RestClient restClient;

    public MetaWhatsAppCloudApiClient(
            WhatsAppCloudApiProperties properties,
            RestClient.Builder restClientBuilder) {
        this.properties = properties;
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
        } catch (RestClientException exception) {
            throw new WhatsAppCloudApiException("Meta WhatsApp template submission failed", exception);
        }
    }

    private MetaTemplateRequest toRequest(WhatsAppTemplateMessage message) {
        List<MetaParameter> parameters = message.bodyParameters()
                .stream()
                .map(value -> new MetaParameter("text", value))
                .toList();
        return new MetaTemplateRequest(
                "whatsapp",
                "individual",
                destinationDigits(message.destination()),
                "template",
                new MetaTemplate(
                        required(message.templateName(), "Template name"),
                        new MetaLanguage(required(message.languageCode(), "Template language")),
                        List.of(new MetaComponent("body", parameters))));
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

    record MetaComponent(
            String type,
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
}
