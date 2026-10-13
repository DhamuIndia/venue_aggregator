package com.staminal.venue.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class VenueDiscoveryMigrationSafetyTest {

    private static final String MIGRATION =
            "/db/migration/V39__venue_discovery_foundation.sql";

    @Test
    void migrationIsAdditiveAndPreservesExistingBusinessData() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("create table venue_discovery_runs")
                .contains("create table venue_discovery_candidates")
                .contains("create table venue_discovery_run_candidates")
                .contains("create table venue_candidate_assignments")
                .contains("create table venue_claims")
                .contains("create table venue_outreach_events")
                .doesNotContain("on delete cascade")
                .doesNotContain("alter table halls")
                .doesNotContain("alter table vendors")
                .doesNotContain("alter table users")
                .doesNotContain("update halls")
                .doesNotContain("update vendors")
                .doesNotContain("update users")
                .doesNotContain("insert into halls")
                .doesNotContain("delete from")
                .doesNotContain("truncate ")
                .doesNotContain("drop table");
    }

    @Test
    void candidatesStoreOnlyProviderIdentifierAndWorkflowMetadata() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("source_place_id varchar(255) not null")
                .contains("unique (source, source_place_id)")
                .contains("unique (discovery_run_id, candidate_id)")
                .doesNotContain("display_name")
                .doesNotContain("formatted_address")
                .doesNotContain("phone_number")
                .doesNotContain("website_uri")
                .doesNotContain("photo_url")
                .doesNotContain("photo_name")
                .doesNotContain("google_rating")
                .doesNotContain("google_review");
    }

    @Test
    void databaseEnforcesClaimAndAssignmentSafety() throws IOException {
        String sql = migrationSql();

        assertThat(sql)
                .contains("token_hash varchar(64) not null unique")
                .contains("token_hash ~ '^[0-9a-f]{64}$'")
                .contains("where status in ('issued', 'verified')")
                .contains("where unassigned_at is null")
                .contains("status <> 'claimed' or linked_hall_id is not null")
                .contains("(status = 'duplicate') = (duplicate_of_candidate_id is not null)");
    }

    @Test
    void nearbyRunsRequireBoundedCoordinatesAndRadius() throws IOException {
        assertThat(migrationSql())
                .contains("center_latitude is null or center_latitude between -90 and 90")
                .contains("center_longitude is null or center_longitude between -180 and 180")
                .contains("radius_meters is null or radius_meters between 1 and 50000")
                .contains("center_latitude is not null")
                .contains("center_longitude is not null")
                .contains("radius_meters is not null");
    }

    @Test
    void outreachHistoryDoesNotPersistContactDestinations() throws IOException {
        String outreachSql = migrationSql().substring(
                migrationSql().indexOf("create table venue_outreach_events"));

        assertThat(outreachSql)
                .doesNotContain("destination")
                .doesNotContain("phone_number")
                .doesNotContain("email_address")
                .doesNotContain("whatsapp_number");
    }

    private String migrationSql() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(MIGRATION)) {
            assertThat(stream)
                    .as("Venue discovery foundation migration should be on the test classpath")
                    .isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
        }
    }
}
