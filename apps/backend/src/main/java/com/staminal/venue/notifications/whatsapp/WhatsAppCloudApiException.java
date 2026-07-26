package com.staminal.venue.notifications.whatsapp;

public class WhatsAppCloudApiException extends RuntimeException {

    public WhatsAppCloudApiException(String message) {
        super(message);
    }

    public WhatsAppCloudApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
