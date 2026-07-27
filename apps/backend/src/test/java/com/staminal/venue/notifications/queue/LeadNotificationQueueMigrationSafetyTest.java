package com.staminal.venue.notifications.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class LeadNotificationQueueMigrationSafetyTest {

    private static final String MIGRATION = "/db/migration/V30__lead_notification_queue.sql";

    @Test
    void migrationIsAdditiveAndDoesNotRewriteExistingBusinessData() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create table lead_notification_jobs")
                .contains("references vendor_leads(id) on delete cascade")
                .contains("references vendors(id) on delete cascade")
                .contains("references customer_requirements(id) on delete cascade")
                .doesNotContain("insert into lead_notification_jobs")
                .doesNotContain("update vendor_leads")
                .doesNotContain("update vendors")
                .doesNotContain("update customer_requirements")
                .doesNotContain("delete from")
                .doesNotContain("truncate ")
                .doesNotContain("drop table");
    }

    @Test
    void databasePreventsDuplicateLeadDeliveries() throws IOException {
        assertThat(migrationSql())
                .contains("constraint uq_lead_notification_jobs_delivery")
                .contains("unique (vendor_lead_id, channel, notification_type)")
                .contains("where status = 'queued'");
    }

    @Test
    void queuePayloadDoesNotPersistCustomerContactDetails() throws IOException {
        assertThat(migrationSql())
                .doesNotContain("customer_name")
                .doesNotContain("customer_phone")
                .doesNotContain("customer_email");
    }

    private String migrationSql() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream).as("Phase 2 migration should be present on the test classpath").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }
    }
}
