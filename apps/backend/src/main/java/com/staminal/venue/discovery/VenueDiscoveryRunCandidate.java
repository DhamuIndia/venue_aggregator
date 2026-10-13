package com.staminal.venue.discovery;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "venue_discovery_run_candidates",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_venue_discovery_run_candidates",
                columnNames = {"discovery_run_id", "candidate_id"}))
@Getter
@Setter
public class VenueDiscoveryRunCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "discovery_run_id", nullable = false)
    private VenueDiscoveryRun discoveryRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private VenueDiscoveryCandidate candidate;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (observedAt == null) {
            observedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }
}
