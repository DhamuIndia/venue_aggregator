package com.staminal.venue.vendors.MakeUp;

import java.math.BigDecimal;

public class CreateVendorMakeupRequest {

    private Long vendorId;

    private Integer experienceYears;

    private Boolean bridalMakeup;

    private Boolean homeService;

    private String productsUsed;

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