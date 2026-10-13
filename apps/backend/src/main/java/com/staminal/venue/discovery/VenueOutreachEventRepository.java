package com.staminal.venue.discovery;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueOutreachEventRepository extends JpaRepository<VenueOutreachEvent, Long> {

    List<VenueOutreachEvent> findByCandidate_IdOrderByOccurredAtDesc(Long candidateId);
}
