package com.staminal.venue.notifications.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WhatsAppCloudApiPropertiesTest {

    @Test
    void sendingIsDisabledByDefault() {
        WhatsAppCloudApiProperties properties = new WhatsAppCloudApiProperties();

        assertThat(properties.isSendingEnabled()).isFalse();
        assertThat(properties.getGraphApiBaseUrl()).isEqualTo("https://graph.facebook.com");
        assertThat(properties.getLeadTemplateLanguage()).isEqualTo("en");
        assertThat(properties.isWebhookEnabled()).isFalse();
        assertThat(properties.getMaxAttempts()).isEqualTo(3);
    }

    @Test
    void enabledSendingRequiresExplicitMetaConfiguration() {
        WhatsAppCloudApiProperties properties = new WhatsAppCloudApiProperties();
        properties.setSendingEnabled(true);

        assertThatThrownBy(properties::validateForSending)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Graph API version");
    }

    @Test
    void completeConfigurationPassesValidation() {
        WhatsAppCloudApiProperties properties = readyProperties();

        assertThatCode(properties::validateForSending).doesNotThrowAnyException();
    }

    @Test
    void webhookRequiresIndependentVerifyTokenAndAppSecret() {
        WhatsAppCloudApiProperties properties = new WhatsAppCloudApiProperties();
        properties.setWebhookEnabled(true);

        assertThatThrownBy(properties::validateWebhookConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verify token");

        properties.setWebhookVerifyToken("venue-mart-webhook-token");
        properties.setAppSecret("meta-app-secret");
        assertThatCode(properties::validateWebhookConfiguration)
                .doesNotThrowAnyException();
    }

    static WhatsAppCloudApiProperties readyProperties() {
        WhatsAppCloudApiProperties properties = new WhatsAppCloudApiProperties();
        properties.setSendingEnabled(true);
        properties.setGraphApiVersion("v99.0");
        properties.setPhoneNumberId("1234567890");
        properties.setAccessToken("test-system-user-token");
        properties.setLeadTemplateName("new_matching_lead_v1");
        properties.setLeadTemplateLanguage("en");
        properties.setBatchSize(20);
        properties.setMaxAttempts(3);
        properties.setRetryInitialDelayMs(60000);
        properties.setRetryMaxDelayMs(3600000);
        return properties;
    }
}
