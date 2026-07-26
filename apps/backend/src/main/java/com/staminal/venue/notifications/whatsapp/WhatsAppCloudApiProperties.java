package com.staminal.venue.notifications.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.notifications.whatsapp")
public class WhatsAppCloudApiProperties {

    private boolean sendingEnabled;
    private String graphApiBaseUrl = "https://graph.facebook.com";
    private String graphApiVersion = "";
    private String phoneNumberId = "";
    private String accessToken = "";
    private String leadTemplateName = "";
    private String leadTemplateLanguage = "en";
    private int batchSize = 20;
    private long pollIntervalMs = 15000;
    private long initialDelayMs = 15000;
    private boolean webhookEnabled;
    private String webhookVerifyToken = "";
    private String appSecret = "";
    private int maxAttempts = 3;
    private long retryInitialDelayMs = 60000;
    private long retryMaxDelayMs = 3600000;

    public void validateForSending() {
        required(graphApiBaseUrl, "WhatsApp Graph API base URL");
        required(graphApiVersion, "WhatsApp Graph API version");
        required(phoneNumberId, "WhatsApp phone number id");
        required(accessToken, "WhatsApp access token");
        required(leadTemplateName, "WhatsApp lead template name");
        required(leadTemplateLanguage, "WhatsApp lead template language");
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalStateException("WhatsApp batch size must be between 1 and 100");
        }
        validateRetryConfiguration();
    }

    public void validateWebhookConfiguration() {
        required(webhookVerifyToken, "WhatsApp webhook verify token");
        required(appSecret, "Meta app secret");
        validateRetryConfiguration();
    }

    private void validateRetryConfiguration() {
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalStateException("WhatsApp maximum attempts must be between 1 and 10");
        }
        if (retryInitialDelayMs < 1000) {
            throw new IllegalStateException("WhatsApp retry initial delay must be at least 1000 ms");
        }
        if (retryMaxDelayMs < retryInitialDelayMs) {
            throw new IllegalStateException(
                    "WhatsApp retry maximum delay must not be less than the initial delay");
        }
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(label + " must be configured before sending is enabled");
        }
        return value.trim();
    }

    public boolean isSendingEnabled() {
        return sendingEnabled;
    }

    public void setSendingEnabled(boolean sendingEnabled) {
        this.sendingEnabled = sendingEnabled;
    }

    public String getGraphApiBaseUrl() {
        return graphApiBaseUrl;
    }

    public void setGraphApiBaseUrl(String graphApiBaseUrl) {
        this.graphApiBaseUrl = graphApiBaseUrl;
    }

    public String getGraphApiVersion() {
        return graphApiVersion;
    }

    public void setGraphApiVersion(String graphApiVersion) {
        this.graphApiVersion = graphApiVersion;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getLeadTemplateName() {
        return leadTemplateName;
    }

    public void setLeadTemplateName(String leadTemplateName) {
        this.leadTemplateName = leadTemplateName;
    }

    public String getLeadTemplateLanguage() {
        return leadTemplateLanguage;
    }

    public void setLeadTemplateLanguage(String leadTemplateLanguage) {
        this.leadTemplateLanguage = leadTemplateLanguage;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public void setInitialDelayMs(long initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
    }

    public boolean isWebhookEnabled() {
        return webhookEnabled;
    }

    public void setWebhookEnabled(boolean webhookEnabled) {
        this.webhookEnabled = webhookEnabled;
    }

    public String getWebhookVerifyToken() {
        return webhookVerifyToken;
    }

    public void setWebhookVerifyToken(String webhookVerifyToken) {
        this.webhookVerifyToken = webhookVerifyToken;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getRetryInitialDelayMs() {
        return retryInitialDelayMs;
    }

    public void setRetryInitialDelayMs(long retryInitialDelayMs) {
        this.retryInitialDelayMs = retryInitialDelayMs;
    }

    public long getRetryMaxDelayMs() {
        return retryMaxDelayMs;
    }

    public void setRetryMaxDelayMs(long retryMaxDelayMs) {
        this.retryMaxDelayMs = retryMaxDelayMs;
    }
}
