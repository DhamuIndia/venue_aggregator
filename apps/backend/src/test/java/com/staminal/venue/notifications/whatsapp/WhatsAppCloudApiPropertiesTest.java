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

    static WhatsAppCloudApiProperties readyProperties() {
        WhatsAppCloudApiProperties properties = new WhatsAppCloudApiProperties();
        properties.setSendingEnabled(true);
        properties.setGraphApiVersion("v99.0");
        properties.setPhoneNumberId("1234567890");
        properties.setAccessToken("test-system-user-token");
        properties.setLeadTemplateName("new_matching_lead_v1");
        properties.setLeadTemplateLanguage("en");
        properties.setBatchSize(20);
        return properties;
    }
}
