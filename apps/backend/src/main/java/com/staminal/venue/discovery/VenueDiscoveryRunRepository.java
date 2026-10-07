package com.staminal.venue.discovery;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueDiscoveryRunRepository extends JpaRepository<VenueDiscoveryRun, Long> {

    List<VenueDiscoveryRun> findByStatusOrderByCreatedAtDesc(VenueDiscoveryRunStatus status);
}
