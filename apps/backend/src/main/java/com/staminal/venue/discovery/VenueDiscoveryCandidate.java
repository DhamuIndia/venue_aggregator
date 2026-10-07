package com.staminal.venue.discovery;

import java.time.Instant;

import com.staminal.venue.halls.Entity.Halls;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "venue_discovery_candidates",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_venue_discovery_candidates_source_place",
                columnNames = {"source", "source_place_id"}))
@Getter
@Setter
public class VenueDiscoveryCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VenueDiscoverySource source;

    @Column(name = "source_place_id", nullable = false, length = 255)
    private String sourcePlaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VenueDiscoveryCandidateStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_hall_id")
    private Halls linkedHall;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_candidate_id")
    private VenueDiscoveryCandidate duplicateOfCandidate;

    @Column(name = "internal_notes", columnDefinition = "text")
    private String internalNotes;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @Column(name = "source_checked_at", nullable = false)
    private Instant sourceCheckedAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (source == null) {
            source = VenueDiscoverySource.GOOGLE_PLACES;
        }
        if (status == null) {
            status = VenueDiscoveryCandidateStatus.DISCOVERED;
        }
        if (discoveredAt == null) {
            discoveredAt = now;
        }
        if (sourceCheckedAt == null) {
            sourceCheckedAt = now;
        }
        if (statusChangedAt == null) {
            statusChangedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
