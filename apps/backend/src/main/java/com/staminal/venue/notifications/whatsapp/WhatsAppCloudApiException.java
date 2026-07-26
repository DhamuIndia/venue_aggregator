package com.staminal.venue.notifications.whatsapp;

public class WhatsAppCloudApiException extends RuntimeException {

    private final Integer providerCode;
    private final String providerTitle;
    private final boolean temporary;
    private final boolean retrySafe;

    public WhatsAppCloudApiException(String message) {
        this(null, null, message, false, false, null);
    }

    public WhatsAppCloudApiException(String message, Throwable cause) {
        this(null, null, message, false, false, cause);
    }

    public WhatsAppCloudApiException(
            Integer providerCode,
            String providerTitle,
            String message,
            boolean temporary,
            boolean retrySafe,
            Throwable cause) {
        super(message, cause);
        this.providerCode = providerCode;
        this.providerTitle = providerTitle;
        this.temporary = temporary;
        this.retrySafe = retrySafe;
    }

    public Integer getProviderCode() {
        return providerCode;
    }

    public String getProviderTitle() {
        return providerTitle;
    }

    public boolean isTemporary() {
        return temporary;
    }

    public boolean isRetrySafe() {
        return retrySafe;
    }
}
