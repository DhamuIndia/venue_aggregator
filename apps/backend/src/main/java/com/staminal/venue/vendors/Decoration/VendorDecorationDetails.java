package com.staminal.venue.vendors.Decoration;

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
@Table(name = "vendor_decoration_details")
public class VendorDecorationDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendors vendor;

    private int experienceYears;

    private boolean flowerDecorationAvailable;

    private boolean stageDecorationAvailable;

    private boolean themeDecorationAvailable;

    private BigDecimal startingPrice;

    private boolean balloonDecorationAvailable;

    public boolean isBalloonDecorationAvailable() {
        return balloonDecorationAvailable;
    }

    public void setBalloonDecorationAvailable(boolean balloonDecorationAvailable) {
        this.balloonDecorationAvailable = balloonDecorationAvailable;
    }

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

    public int getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(int experienceYears) {
        this.experienceYears = experienceYears;
    }

    public boolean isFlowerDecorationAvailable() {
        return flowerDecorationAvailable;
    }

    public void setFlowerDecorationAvailable(boolean flowerDecorationAvailable) {
        this.flowerDecorationAvailable = flowerDecorationAvailable;
    }

    public boolean isStageDecorationAvailable() {
        return stageDecorationAvailable;
    }

    public void setStageDecorationAvailable(boolean stageDecorationAvailable) {
        this.stageDecorationAvailable = stageDecorationAvailable;
    }

    public boolean isThemeDecorationAvailable() {
        return themeDecorationAvailable;
    }

    public void setThemeDecorationAvailable(boolean themeDecorationAvailable) {
        this.themeDecorationAvailable = themeDecorationAvailable;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(BigDecimal startingPrice) {
        this.startingPrice = startingPrice;
    }

}
