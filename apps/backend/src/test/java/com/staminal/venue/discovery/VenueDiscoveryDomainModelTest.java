package com.staminal.venue.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VenueDiscoveryDomainModelTest {

    @Test
    void discoveryRunDefaultsToCreatedGooglePlacesRun() {
        VenueDiscoveryRun run = new VenueDiscoveryRun();

        run.onCreate();

        assertThat(run.getSource()).isEqualTo(VenueDiscoverySource.GOOGLE_PLACES);
        assertThat(run.getStatus()).isEqualTo(VenueDiscoveryRunStatus.CREATED);
        assertThat(run.getCreatedAt()).isNotNull();
        assertThat(run.getUpdatedAt()).isNotNull();
    }

    @Test
    void candidateDefaultsToNonPublicDiscoveredState() {
        VenueDiscoveryCandidate candidate = new VenueDiscoveryCandidate();
        candidate.setSourcePlaceId("ChIJ-example");

        candidate.onCreate();

        assertThat(candidate.getSource()).isEqualTo(VenueDiscoverySource.GOOGLE_PLACES);
        assertThat(candidate.getStatus()).isEqualTo(VenueDiscoveryCandidateStatus.DISCOVERED);
        assertThat(candidate.getLinkedHall()).isNull();
        assertThat(candidate.getDiscoveredAt()).isNotNull();
        assertThat(candidate.getSourceCheckedAt()).isNotNull();
        assertThat(candidate.getStatusChangedAt()).isNotNull();
    }

    @Test
    void claimDefaultsToIssuedPhoneVerificationWithoutPlaintextTokenField() {
        VenueClaim claim = new VenueClaim();

        claim.onCreate();

        assertThat(claim.getStatus()).isEqualTo(VenueClaimStatus.ISSUED);
        assertThat(claim.getVerificationMethod())
                .isEqualTo(VenueClaimVerificationMethod.PHONE_OTP);
        assertThat(VenueClaim.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .contains("tokenHash")
                .doesNotContain("token", "plainToken", "claimToken");
    }

    @Test
    void assignmentAndOutreachEventsReceiveAppendOnlyTimestamps() {
        VenueCandidateAssignment assignment = new VenueCandidateAssignment();
        VenueDiscoveryRunCandidate observation = new VenueDiscoveryRunCandidate();
        VenueOutreachEvent outreach = new VenueOutreachEvent();

        assignment.onCreate();
        observation.onCreate();
        outreach.onCreate();

        assertThat(assignment.getAssignedAt()).isNotNull();
        assertThat(assignment.getCreatedAt()).isNotNull();
        assertThat(observation.getObservedAt()).isNotNull();
        assertThat(observation.getCreatedAt()).isNotNull();
        assertThat(outreach.getOccurredAt()).isNotNull();
        assertThat(outreach.getCreatedAt()).isNotNull();
    }
}
