package com.staminal.venue.discovery;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueDiscoveryRunCandidateRepository
        extends JpaRepository<VenueDiscoveryRunCandidate, Long> {

    List<VenueDiscoveryRunCandidate> findByDiscoveryRun_IdOrderByObservedAtDesc(Long discoveryRunId);

    List<VenueDiscoveryRunCandidate> findByCandidate_IdOrderByObservedAtDesc(Long candidateId);
}
