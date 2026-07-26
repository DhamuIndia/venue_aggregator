package com.staminal.venue.notifications.whatsapp;

public interface WhatsAppCloudApiClient {

    WhatsAppCloudApiSendResult sendTemplate(WhatsAppTemplateMessage message);
}
