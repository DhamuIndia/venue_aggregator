package com.staminal.venue.discovery;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

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

    Page<VenueDiscoveryCandidate> findByStatus(VenueDiscoveryCandidateStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select candidate from VenueDiscoveryCandidate candidate where candidate.id = :id")
    Optional<VenueDiscoveryCandidate> findByIdForUpdate(@Param("id") Long id);
}
