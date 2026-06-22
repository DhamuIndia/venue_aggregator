package com.staminal.venue.vendors.MakeUp;

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
@Table(name="vendor_makeup_details")
public class VendorMakeupDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendors vendor;

    private Integer experienceYears;

    private  Boolean bridalMakeup;

    private Boolean homeService;

    private String productsUsed;

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

    public Boolean getBridalMakeup() {
        return bridalMakeup;
    }

    public void setBridalMakeup(Boolean bridalMakeup) {
        this.bridalMakeup = bridalMakeup;
    }

    public Boolean getHomeService() {
        return homeService;
    }

    public void setHomeService(Boolean homeService) {
        this.homeService = homeService;
    }

    public String getProductsUsed() {
        return productsUsed;
    }

    public void setProductsUsed(String productsUsed) {
        this.productsUsed = productsUsed;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(BigDecimal startingPrice) {
        this.startingPrice = startingPrice;
    }

    
    
}
