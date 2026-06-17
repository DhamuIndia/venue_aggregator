package com.staminal.venue.vendors.Photography;

import java.math.BigDecimal;

import com.staminal.venue.vendors.Entity.Vendors;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "vendor_photography_details")
public class VendorPhotographyDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendors vendor;

    private Integer experienceYears;

    private Boolean candidPhotography;

    private Boolean videographyAvailable;

    private Boolean droneAvailable;

    private Boolean albumIncluded;

    private BigDecimal startingPrice;

    public long getId() {
        return id;
    }

    public void setId(long id) {
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
