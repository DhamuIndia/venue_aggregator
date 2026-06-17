package com.staminal.venue.vendors.Dj;

import java.math.BigDecimal;
import java.time.Instant;

import com.staminal.venue.vendors.Entity.Vendors;

import jakarta.persistence.*;
@Entity
@Table(name = "vendor_dj_details")
public class VendorDjDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendors vendor;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(name = "sound_system_available")
    private Boolean soundSystemAvailable;

    @Column(name = "travel_distance_km")
    private Integer travelDistanceKm;

    @Column(name = "starting_price")
    private BigDecimal startingPrice;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Vendors getVendor() {
        return vendor;
    }

    public void setVendor(Vendors vendor) {
        this.vendor = vendor;
    }

    public Integer getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(Integer experienceYears) {
        this.experienceYears = experienceYears;
    }

    public Boolean getSoundSystemAvailable() {
        return soundSystemAvailable;
    }

    public void setSoundSystemAvailable(Boolean soundSystemAvailable) {
        this.soundSystemAvailable = soundSystemAvailable;
    }

    public Integer getTravelDistanceKm() {
        return travelDistanceKm;
    }

    public void setTravelDistanceKm(Integer travelDistanceKm) {
        this.travelDistanceKm = travelDistanceKm;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(BigDecimal startingPrice) {
        this.startingPrice = startingPrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}