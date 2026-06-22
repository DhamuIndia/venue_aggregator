package com.staminal.venue.vendors.Decoration;

import java.math.BigDecimal;

public class VendorDecorationResponse {

    private Long id;

    private Long vendorId;

    private Integer experienceYears;

    private Boolean flowerDecorationAvailable;

    private Boolean balloonDecorationAvailable;

    private Boolean stageDecorationAvailable;

    private Boolean themeDecorationAvailable;

    private BigDecimal startingPrice;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Boolean getFlowerDecorationAvailable() {
        return flowerDecorationAvailable;
    }

    public void setFlowerDecorationAvailable(Boolean flowerDecorationAvailable) {
        this.flowerDecorationAvailable = flowerDecorationAvailable;
    }

    public Boolean getBalloonDecorationAvailable() {
        return balloonDecorationAvailable;
    }

    public void setBalloonDecorationAvailable(Boolean balloonDecorationAvailable) {
        this.balloonDecorationAvailable = balloonDecorationAvailable;
    }

    public Boolean getStageDecorationAvailable() {
        return stageDecorationAvailable;
    }

    public void setStageDecorationAvailable(Boolean stageDecorationAvailable) {
        this.stageDecorationAvailable = stageDecorationAvailable;
    }

    public Boolean getThemeDecorationAvailable() {
        return themeDecorationAvailable;
    }

    public void setThemeDecorationAvailable(Boolean themeDecorationAvailable) {
        this.themeDecorationAvailable = themeDecorationAvailable;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(BigDecimal startingPrice) {
        this.startingPrice = startingPrice;
    }

}
