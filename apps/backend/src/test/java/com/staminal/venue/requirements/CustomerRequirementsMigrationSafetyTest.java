package com.staminal.venue.requirements;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class CustomerRequirementsMigrationSafetyTest {

    private static final String MIGRATION = "/db/migration/V25__customer_requirements_foundation.sql";

    @Test
    void migrationIsAdditiveAndLeavesExistingLeadRowsValid() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create table customer_requirements")
                .contains("create table requirement_services")
                .contains("add column requirement_id bigint;")
                .contains("on delete set null")
                .doesNotContain("add column requirement_id bigint not null")
                .doesNotContain("drop table")
                .doesNotContain("truncate ")
                .doesNotContain("delete from")
                .doesNotContain("update vendor_leads");
    }

    @Test
    void migrationPreventsDuplicateFanOutWithoutConstrainingDirectLeads() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create unique index uq_vendor_leads_requirement_vendor")
                .contains("on vendor_leads(requirement_id, vendor_id)")
                .contains("where requirement_id is not null");
    }

    private String migrationSql() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream).as("Phase 0 migration should be present on the test classpath").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        }
    }
}
