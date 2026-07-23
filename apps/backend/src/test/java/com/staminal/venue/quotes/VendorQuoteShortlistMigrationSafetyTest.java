package com.staminal.venue.quotes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class VendorQuoteShortlistMigrationSafetyTest {

    private static final String MIGRATION = "/db/migration/V27__customer_quote_shortlist.sql";

    @Test
    void migrationIsAdditiveAndDefaultsExistingQuotesToNotShortlisted() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("add column shortlisted boolean not null default false")
                .contains("add column shortlisted_at timestamptz")
                .contains("constraint ck_vendor_quotes_shortlist_timestamp")
                .contains("create index idx_vendor_quotes_shortlisted")
                .doesNotContain("drop table")
                .doesNotContain("truncate ")
                .doesNotContain("delete from")
                .doesNotContain("update vendor_quotes");
    }

    private String migrationSql() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream).as("Phase 4A migration should be present on the test classpath").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }
    }
}
