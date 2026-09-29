package com.staminal.venue.discovery;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueDiscoveryCandidateRepository
        extends JpaRepository<VenueDiscoveryCandidate, Long> {

    boolean existsBySourceAndSourcePlaceId(
            VenueDiscoverySource source,
            String sourcePlaceId);

    Optional<VenueDiscoveryCandidate> findBySourceAndSourcePlaceId(
            VenueDiscoverySource source,
            String sourcePlaceId);

    List<VenueDiscoveryCandidate> findByStatusOrderByStatusChangedAtDesc(
            VenueDiscoveryCandidateStatus status);
}
