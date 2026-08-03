package com.staminal.venue.notifications.preferences;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class VendorNotificationPreferenceMigrationSafetyTest {

    private static final String MIGRATION =
            "/db/migration/V29__vendor_notification_preferences.sql";

    @Test
    void migrationIsAdditiveAndDoesNotSubscribeOrRewriteExistingVendors() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create table vendor_notification_preferences")
                .contains("whatsapp_lead_notifications_enabled boolean not null default false")
                .contains("whatsapp_lead_notifications_paused boolean not null default false")
                .contains("references vendors(id) on delete cascade")
                .doesNotContain("insert into vendor_notification_preferences")
                .doesNotContain("update vendors")
                .doesNotContain("update vendor_notification_preferences")
                .doesNotContain("delete from")
                .doesNotContain("truncate ")
                .doesNotContain("drop table");
    }

    @Test
    void migrationRequiresConsentForEnabledPreferencesAndPreventsInvalidPauseState() throws IOException {
        assertThat(migrationSql())
                .contains("ck_vendor_notification_preferences_enabled_consent")
                .contains("whatsapp_number is not null")
                .contains("whatsapp_consented_at is not null")
                .contains("whatsapp_consent_source is not null")
                .contains("whatsapp_opted_out_at is null")
                .contains("ck_vendor_notification_preferences_pause")
                .contains("whatsapp_lead_notifications_enabled")
                .contains("whatsapp_paused_at is not null");
    }

    private String migrationSql() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream).as("Phase 1 migration should be present on the test classpath").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }
    }
}
