package com.staminal.venue.discovery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueClaimRepository extends JpaRepository<VenueClaim, Long> {

    Optional<VenueClaim> findByTokenHash(String tokenHash);

    Optional<VenueClaim> findByCandidate_IdAndStatusIn(
            Long candidateId,
            Set<VenueClaimStatus> statuses);

    List<VenueClaim> findByStatusInAndExpiresAtBefore(
            Set<VenueClaimStatus> statuses,
            Instant expiresAt);
}
