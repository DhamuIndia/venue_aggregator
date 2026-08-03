package com.staminal.venue.leads;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class VendorLeadReferenceMigrationSafetyTest {

    private static final String MIGRATION =
            "/db/migration/V33__secure_vendor_lead_references.sql";

    @Test
    void migrationBackfillsOnlyOpaqueLeadReferencesAndPreservesBusinessData()
            throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("add column public_reference varchar(25)")
                .contains("uq_vendor_leads_public_reference")
                .contains("add column lead_reference varchar(25)")
                .contains("update vendor_leads")
                .contains("update lead_notification_jobs")
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
                    .as("Phase 5 migration should be present on the test classpath")
                    .isNotNull();
            return new String(
                    stream.readAllBytes(),
                    StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
        }
    }
}
