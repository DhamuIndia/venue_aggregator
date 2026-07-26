package com.staminal.venue.notifications.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class WhatsAppCloudApiPropertiesTest {

    @Test
    void sendingIsDisabledByDefault() {
        WhatsAppCloudApiProperties properties = new WhatsAppCloudApiProperties();

        assertThat(properties.isSendingEnabled()).isFalse();
        assertThat(properties.getGraphApiBaseUrl()).isEqualTo("https://graph.facebook.com");
        assertThat(properties.getLeadTemplateLanguage()).isEqualTo("en");
        assertThat(properties.getLeadTemplateUrlButtonIndex()).isZero();
        assertThat(properties.getRolloutAllowedVendorIds()).isEmpty();
        assertThat(properties.isWebhookEnabled()).isFalse();
        assertThat(properties.getMaxAttempts()).isEqualTo(3);
    }

    @Test
    void commaSeparatedRolloutVendorIdsBindAsASortedFailClosedSet() {
        WhatsAppCloudApiProperties properties = new Binder(
                new MapConfigurationPropertySource(Map.of(
                        "app.notifications.whatsapp.rollout-allowed-vendor-ids",
                        "502,501")))
                .bind(
                        "app.notifications.whatsapp",
                        Bindable.of(WhatsAppCloudApiProperties.class))
                .orElseThrow(() -> new AssertionError("WhatsApp properties did not bind"));

        assertThat(properties.getRolloutAllowedVendorIds()).containsExactly(501L, 502L);
        assertThat(properties.isRolloutVendorAllowed(501L)).isTrue();
        assertThat(properties.isRolloutVendorAllowed(999L)).isFalse();
    }

    @Test
    void emptyEnvironmentAllowlistBindsToNoVendors() {
        WhatsAppCloudApiProperties properties = new Binder(
                new MapConfigurationPropertySource(Map.of(
                        "app.notifications.whatsapp.rollout-allowed-vendor-ids",
                        "")))
                .bind(
                        "app.notifications.whatsapp",
                        Bindable.of(WhatsAppCloudApiProperties.class))
                .orElseGet(WhatsAppCloudApiProperties::new);

        assertThat(properties.getRolloutAllowedVendorIds()).isEmpty();
    }

    @Test
    void rolloutVendorIdsRejectNonPositiveDatabaseIds() {
        WhatsAppCloudApiProperties properties = new WhatsAppCloudApiProperties();

        assertThatThrownBy(() -> properties.setRolloutAllowedVendorIds(Set.of(0L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
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
    void dynamicUrlButtonIndexMustMatchAMetaTemplateButton() {
        WhatsAppCloudApiProperties properties = readyProperties();
        properties.setLeadTemplateUrlButtonIndex(3);

        assertThatThrownBy(properties::validateForSending)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("URL button index");
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
        properties.setRolloutAllowedVendorIds(Set.of(501L));
        properties.setBatchSize(20);
        properties.setMaxAttempts(3);
        properties.setRetryInitialDelayMs(60000);
        properties.setRetryMaxDelayMs(3600000);
        return properties;
    }
}
