package com.staminal.venue.vendors.Dj;

import java.math.BigDecimal;

public class CreateVendorDjRequest {

    private Long vendorId;

    private Integer experienceYears;

    private Boolean soundSystemAvailable;

    private Integer travelDistanceKm;

    private BigDecimal startingPrice;

    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
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
}