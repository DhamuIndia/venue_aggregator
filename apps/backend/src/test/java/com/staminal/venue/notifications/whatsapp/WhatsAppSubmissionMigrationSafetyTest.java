package com.staminal.venue.notifications.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class WhatsAppSubmissionMigrationSafetyTest {

    private static final String MIGRATION =
            "/db/migration/V31__whatsapp_cloud_api_submission.sql";

    @Test
    void migrationOnlyAddsSubmissionMetadataAndDoesNotTouchExistingBusinessRows() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("add column provider_message_id varchar(255)")
                .contains("add column processing_started_at timestamptz")
                .contains("add column submitted_at timestamptz")
                .contains("uq_lead_notification_jobs_provider_message")
                .doesNotContain("update lead_notification_jobs")
                .doesNotContain("update vendor_leads")
                .doesNotContain("update vendors")
                .doesNotContain("update halls")
                .doesNotContain("delete from")
                .doesNotContain("truncate ")
                .doesNotContain("drop table");
    }

    private String migrationSql() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream).as("Phase 3 migration should be present on the test classpath").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }
    }
}
