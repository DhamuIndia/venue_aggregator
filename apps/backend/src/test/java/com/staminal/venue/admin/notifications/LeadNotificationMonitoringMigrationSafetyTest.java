package com.staminal.venue.admin.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class LeadNotificationMonitoringMigrationSafetyTest {

    private static final String MIGRATION =
            "/db/migration/V34__lead_notification_monitoring.sql";

    @Test
    void migrationAddsMonitoringRecordsWithoutChangingBusinessData()
            throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create table lead_notification_evaluations")
                .contains("insert into lead_notification_evaluations")
                .contains("legacy_not_recorded")
                .contains("idx_lead_notification_evaluations_requirement")
                .doesNotContain("update vendor_leads")
                .doesNotContain("update lead_notification_jobs")
                .doesNotContain("update vendors")
                .doesNotContain("update halls")
                .doesNotContain("update users")
                .doesNotContain("update customer_requirements")
                .doesNotContain("delete from")
                .doesNotContain("truncate ")
                .doesNotContain("drop table");
    }

    private String migrationSql() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream)
                    .as("Phase 6 migration should be present on the test classpath")
                    .isNotNull();
            return new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
        }
    }
}
