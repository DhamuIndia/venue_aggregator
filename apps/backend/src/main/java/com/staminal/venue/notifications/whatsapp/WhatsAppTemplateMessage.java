package com.staminal.venue.notifications.whatsapp;

import java.util.List;

public record WhatsAppTemplateMessage(
        String destination,
        String templateName,
        String languageCode,
        List<String> bodyParameters) {

    public WhatsAppTemplateMessage {
        bodyParameters = bodyParameters == null ? List.of() : List.copyOf(bodyParameters);
    }
}
