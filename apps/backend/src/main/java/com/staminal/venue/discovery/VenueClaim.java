package com.staminal.venue.discovery;

import java.time.Instant;

import com.staminal.venue.admin.Admin;
import com.staminal.venue.users.Entity.User;

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
@Table(name = "venue_claims")
@Getter
@Setter
public class VenueClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private VenueDiscoveryCandidate candidate;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VenueClaimStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", nullable = false, length = 40)
    private VenueClaimVerificationMethod verificationMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimed_by_user_id")
    private User claimedByUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_admin_id", nullable = false)
    private Admin createdByAdmin;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "consent_version", length = 80)
    private String consentVersion;

    @Column(name = "consent_at")
    private Instant consentAt;

    @Column(name = "consent_ip", length = 80)
    private String consentIp;

    @Column(name = "consent_user_agent", length = 500)
    private String consentUserAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (status == null) {
            status = VenueClaimStatus.ISSUED;
        }
        if (verificationMethod == null) {
            verificationMethod = VenueClaimVerificationMethod.PHONE_OTP;
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
