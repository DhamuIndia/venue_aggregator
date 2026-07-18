package com.staminal.venue.reviews.VendorReview;

import java.time.Instant;

import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.reviews.HallReview.ReviewModerationStatus;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.admin.Admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "vendor_reviews")
@Getter
@Setter
public class VendorReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "vendor_lead_id")
    private VendorLead vendorLead;

    @ManyToOne
    @JoinColumn(name = "vendor_id")
    private Vendors vendor;

    @ManyToOne
    @JoinColumn(name = "customer_user_id")
    private User customer;

    private Integer rating;

    @Column(length = 500)
    private String comment;

    private Boolean active;

    @Enumerated(EnumType.STRING)
    private ReviewModerationStatus moderationStatus;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "moderation_reason", length = 500)
    private String moderationReason;

    @ManyToOne
    @JoinColumn(name = "moderated_by_admin_id")
    private Admin moderatedByAdmin;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;

        if (active == null) {
            active = true;
        }

        if (moderationStatus == null) {
            moderationStatus = ReviewModerationStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

}
