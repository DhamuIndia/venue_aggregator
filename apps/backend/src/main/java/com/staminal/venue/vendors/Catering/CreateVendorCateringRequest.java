package com.staminal.venue.vendors.Catering;

import java.math.BigDecimal;

import com.staminal.venue.enums.CateringServiceType;

public class CreateVendorCateringRequest {

    private Long vendorId;

    private Boolean vegAvailable;

    private Boolean nonVegAvailable;

    private CateringServiceType serviceType;

    private Integer minGuestCount;

    private Integer maxGuestCount;

    private Boolean liveCounterAvailable;

    private BigDecimal startingPricePerPlate;

    public Long getVendorId() {
        return vendorId;
    }

    public void setVendorId(Long vendorId) {
        this.vendorId = vendorId;
    }

    public Boolean getVegAvailable() {
        return vegAvailable;
    }

    public void setVegAvailable(Boolean vegAvailable) {
        this.vegAvailable = vegAvailable;
    }

    public Boolean getNonVegAvailable() {
        return nonVegAvailable;
    }

    public void setNonVegAvailable(Boolean nonVegAvailable) {
        this.nonVegAvailable = nonVegAvailable;
    }

    public CateringServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(CateringServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public Integer getMinGuestCount() {
        return minGuestCount;
    }

    public void setMinGuestCount(Integer minGuestCount) {
        this.minGuestCount = minGuestCount;
    }

    public Integer getMaxGuestCount() {
        return maxGuestCount;
    }

    public void setMaxGuestCount(Integer maxGuestCount) {
        this.maxGuestCount = maxGuestCount;
    }

    public Boolean getLiveCounterAvailable() {
        return liveCounterAvailable;
    }

    public void setLiveCounterAvailable(Boolean liveCounterAvailable) {
        this.liveCounterAvailable = liveCounterAvailable;
    }

    public BigDecimal getStartingPricePerPlate() {
        return startingPricePerPlate;
    }

    public void setStartingPricePerPlate(BigDecimal startingPricePerPlate) {
        this.startingPricePerPlate = startingPricePerPlate;
    }

  
}