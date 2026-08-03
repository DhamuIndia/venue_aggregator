package com.staminal.venue.halls.Dto;

import java.math.BigDecimal;

public class Pricing {

    private BigDecimal morningPrice;

    private BigDecimal eveningPrice;
    
    private BigDecimal fullDayPrice;

    public BigDecimal getMorningPrice() {
        return morningPrice;
    }

    public void setMorningPrice(BigDecimal morningPrice) {
        this.morningPrice = morningPrice;
    }

    public BigDecimal getEveningPrice() {
        return eveningPrice;
    }

    public void setEveningPrice(BigDecimal eveningPrice) {
        this.eveningPrice = eveningPrice;
    }

    public BigDecimal getFullDayPrice() {
        return fullDayPrice;
    }

    public void setFullDayPrice(BigDecimal fullDayPrice) {
        this.fullDayPrice = fullDayPrice;
    }

}
