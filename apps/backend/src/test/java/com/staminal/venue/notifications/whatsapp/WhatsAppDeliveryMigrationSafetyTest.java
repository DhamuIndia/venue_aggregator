package com.staminal.venue.notifications.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class WhatsAppDeliveryMigrationSafetyTest {

    private static final String MIGRATION =
            "/db/migration/V32__whatsapp_delivery_tracking_and_retries.sql";

    @Test
    void migrationAddsAttemptsAndOnlyNormalizesLegacyNotificationStatuses()
            throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create table whatsapp_notification_attempts")
                .contains("unique (job_id, attempt_number)")
                .contains("uq_whatsapp_notification_attempt_provider_message")
                .contains("add column failure_reason varchar(1000)")
                .contains("add column next_retry_at timestamptz")
                .contains("when status = 'submitted' then 'sent'")
                .contains("when status = 'send_failed' then 'failed'")
                .doesNotContain("update vendors")
                .doesNotContain("update halls")
                .doesNotContain("update vendor_notification_preferences")
                .doesNotContain("delete from")
                .doesNotContain("truncate ")
                .doesNotContain("drop table");
    }

    private String migrationSql() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream)
                    .as("Phase 4 migration should be present on the test classpath")
                    .isNotNull();
            return new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
        }
    }
}
