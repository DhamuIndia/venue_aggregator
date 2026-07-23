package com.staminal.venue.quotes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class VendorQuoteMigrationSafetyTest {

    private static final String MIGRATION = "/db/migration/V26__vendor_quote_workflow.sql";

    @Test
    void migrationIsAdditiveAndPreservesExistingLeadRows() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("add column decline_reason varchar(1000)")
                .contains("create table vendor_quotes")
                .contains("create table vendor_quote_inclusions")
                .doesNotContain("add column decline_reason varchar(1000) not null")
                .doesNotContain("drop table")
                .doesNotContain("truncate ")
                .doesNotContain("delete from")
                .doesNotContain("update vendor_leads");
    }

    @Test
    void migrationAllowsOnlyOneQuotePerLead() throws IOException {
        assertThat(migrationSql())
                .contains("constraint uq_vendor_quotes_lead unique (vendor_lead_id)")
                .contains("references vendor_leads(id) on delete cascade");
    }

    private String migrationSql() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream).as("Phase 3 migration should be present on the test classpath").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }
    }
}
