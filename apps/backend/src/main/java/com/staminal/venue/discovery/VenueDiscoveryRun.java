package com.staminal.venue.discovery;

import java.time.Instant;

import com.staminal.venue.admin.Admin;

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
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "venue_discovery_runs")
@Getter
@Setter
public class VenueDiscoveryRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VenueDiscoverySource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VenueDiscoveryRunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "search_mode", nullable = false, length = 30)
    private VenueDiscoverySearchMode searchMode;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(length = 120)
    private String area;

    @Column(name = "requested_place_types", nullable = false, length = 500)
    private String requestedPlaceTypes;

    @Column(name = "center_latitude")
    private Double centerLatitude;

    @Column(name = "center_longitude")
    private Double centerLongitude;

    @Column(name = "radius_meters")
    private Integer radiusMeters;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_admin_id", nullable = false)
    private Admin createdByAdmin;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

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
            status = VenueDiscoveryRunStatus.CREATED;
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
