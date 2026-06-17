package com.staminal.venue.vendors.Photography;

import java.math.BigDecimal;

public class CreateVendorPhotographyRequest {

    private Long vendorId;

    private Integer experienceYears;

    private Boolean candidPhotography;

    private Boolean videographyAvailable;

    private Boolean droneAvailable;

    private Boolean albumIncluded;

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

    public Boolean getCandidPhotography() {
        return candidPhotography;
    }

    public void setCandidPhotography(Boolean candidPhotography) {
        this.candidPhotography = candidPhotography;
    }

    public Boolean getVideographyAvailable() {
        return videographyAvailable;
    }

    public void setVideographyAvailable(Boolean videographyAvailable) {
        this.videographyAvailable = videographyAvailable;
    }

    public Boolean getDroneAvailable() {
        return droneAvailable;
    }

    public void setDroneAvailable(Boolean droneAvailable) {
        this.droneAvailable = droneAvailable;
    }

    public Boolean getAlbumIncluded() {
        return albumIncluded;
    }

    public void setAlbumIncluded(Boolean albumIncluded) {
        this.albumIncluded = albumIncluded;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(BigDecimal startingPrice) {
        this.startingPrice = startingPrice;
    }
}