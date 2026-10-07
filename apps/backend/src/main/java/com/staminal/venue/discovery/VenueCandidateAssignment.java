package com.staminal.venue.discovery;

import java.time.Instant;

import com.staminal.venue.admin.Admin;

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
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "venue_candidate_assignments")
@Getter
@Setter
public class VenueCandidateAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private VenueDiscoveryCandidate candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_admin_id", nullable = false)
    private Admin assignedAdmin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by_admin_id", nullable = false)
    private Admin assignedByAdmin;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "unassigned_at")
    private Instant unassignedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (assignedAt == null) {
            assignedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }
}
