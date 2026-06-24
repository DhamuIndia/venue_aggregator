package com.staminal.venue.halls.Dto;

import java.math.BigDecimal;

public class HallResponse {

    private Long id;

    private String name;

    private String description;

    private String coverImageUrl;

    private String city;

    private String area;

    private Integer capacityMax;

    private BigDecimal amount;

    private String hallType;

    private Double ratings;

    private Boolean acAvailable;

    private Boolean carParking;

    private Boolean bikeParking;

    private Boolean diningAvailable;

    private Integer rooms;

    private Boolean generatorAvailable;

    private Boolean liftAvailable;

    private String status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public Integer getCapacityMax() {
        return capacityMax;
    }

    public void setCapacityMax(Integer capacityMax) {
        this.capacityMax = capacityMax;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getHallType() {
        return hallType;
    }

    public void setHallType(String hallType) {
        this.hallType = hallType;
    }

    public Double getRatings() {
        return ratings;
    }

    public void setRatings(Double ratings) {
        this.ratings = ratings;
    }

    public Boolean getAcAvailable() {
        return acAvailable;
    }

    public void setAcAvailable(Boolean acAvailable) {
        this.acAvailable = acAvailable;
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

    public Integer getRooms() {
        return rooms;
    }

    public void setRooms(Integer rooms) {
        this.rooms = rooms;
    }

    public Boolean getGeneratorAvailable() {
        return generatorAvailable;
    }

    public void setGeneratorAvailable(Boolean generatorAvailable) {
        this.generatorAvailable = generatorAvailable;
    }

    public Boolean getLiftAvailable() {
        return liftAvailable;
    }

    public void setLiftAvailable(Boolean liftAvailable) {
        this.liftAvailable = liftAvailable;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}
