package com.staminal.venue.notifications.whatsapp;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record WhatsAppForwardingProperties(boolean enabled, URI endpoint, String phoneNumberId) {
    public WhatsAppForwardingProperties(
            @Value("${WHATSAPP_LEADCAT_FORWARDING_ENABLED:false}") boolean enabled,
            @Value("${WHATSAPP_LEADCAT_FORWARDING_URL:https://crm.staminal.in/webhooks/notifications/whatsapp}") URI endpoint,
            @Value("${WHATSAPP_LEADCAT_FORWARDING_PHONE_NUMBER_ID:}") String phoneNumberId) {
        if (enabled && (!"https".equals(endpoint.getScheme()) || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || endpoint.getFragment() != null
                || phoneNumberId.isBlank())) {
            throw new IllegalStateException("WhatsApp forwarding needs an HTTPS endpoint and a sender phone id");
        }
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.phoneNumberId = phoneNumberId;
    }
}
