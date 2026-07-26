package com.staminal.venue.notifications.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class WhatsAppRetryPolicyTest {

    @Test
    void usesBoundedExponentialBackoffAndStopsAtMaximumAttempts() {
        WhatsAppCloudApiProperties properties = new WhatsAppCloudApiProperties();
        properties.setMaxAttempts(3);
        properties.setRetryInitialDelayMs(60000);
        properties.setRetryMaxDelayMs(3600000);
        WhatsAppRetryPolicy policy = new WhatsAppRetryPolicy(properties);

        Instant beforeFirst = Instant.now();
        Instant firstRetry = policy.nextRetryAt(1);
        Instant beforeSecond = Instant.now();
        Instant secondRetry = policy.nextRetryAt(2);

        assertThat(Duration.between(beforeFirst, firstRetry).toMillis())
                .isBetween(60000L, 60100L);
        assertThat(Duration.between(beforeSecond, secondRetry).toMillis())
                .isBetween(120000L, 120100L);
        assertThat(policy.nextRetryAt(3)).isNull();
    }
}
