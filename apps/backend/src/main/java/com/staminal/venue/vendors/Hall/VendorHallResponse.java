package com.staminal.venue.vendors.Hall;

import java.math.BigDecimal;

import com.staminal.venue.enums.HallType;

public class VendorHallResponse {

    private Long id;

    private Long vendorId;

    private Integer capacityMin;

    private Integer capacityMax;

    private Integer floors;

    private Integer rooms;

    private HallType hallType;

    private Boolean acAvailable;

    private Boolean liftAvailable;

    private Boolean generatorAvailable;

    private Boolean carParking;

    private Boolean bikeParking;

    private Boolean diningAvailable;

    private Integer diningCapacity;

    private BigDecimal amount;

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

    public Integer getCapacityMin() {
        return capacityMin;
    }

    public void setCapacityMin(Integer capacityMin) {
        this.capacityMin = capacityMin;
    }

    public Integer getCapacityMax() {
        return capacityMax;
    }

    public void setCapacityMax(Integer capacityMax) {
        this.capacityMax = capacityMax;
    }

    public Integer getFloors() {
        return floors;
    }

    public void setFloors(Integer floors) {
        this.floors = floors;
    }

    public Integer getRooms() {
        return rooms;
    }

    public void setRooms(Integer rooms) {
        this.rooms = rooms;
    }

    public HallType getHallType() {
        return hallType;
    }

    public void setHallType(HallType hallType) {
        this.hallType = hallType;
    }

    public Boolean getAcAvailable() {
        return acAvailable;
    }

    public void setAcAvailable(Boolean acAvailable) {
        this.acAvailable = acAvailable;
    }

    public Boolean getLiftAvailable() {
        return liftAvailable;
    }

    public void setLiftAvailable(Boolean liftAvailable) {
        this.liftAvailable = liftAvailable;
    }

    public Boolean getGeneratorAvailable() {
        return generatorAvailable;
    }

    public void setGeneratorAvailable(Boolean generatorAvailable) {
        this.generatorAvailable = generatorAvailable;
    }

    public Boolean getCarParking() {
        return carParking;
    }

    public void setCarParking(Boolean carParking) {
        this.carParking = carParking;
    }

    public Boolean getBikeParking() {
        return bikeParking;
    }

    public void setBikeParking(Boolean bikeParking) {
        this.bikeParking = bikeParking;
    }

    public Boolean getDiningAvailable() {
        return diningAvailable;
    }

    public void setDiningAvailable(Boolean diningAvailable) {
        this.diningAvailable = diningAvailable;
    }

    public Integer getDiningCapacity() {
        return diningCapacity;
    }

    public void setDiningCapacity(Integer diningCapacity) {
        this.diningCapacity = diningCapacity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}