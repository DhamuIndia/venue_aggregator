package com.staminal.venue.halls.Dto;

import java.math.BigDecimal;
import java.util.List;

public class CreateHallRequest {

    private String name;

    private String description;

    private String addressLine;

    private String city;

    private String area;

    private String pincode;

    private Double latitude;

    private Double longitude;

    // private Integer capacityMin;

    private Integer capacityMax;

    private Integer floors;

    private Boolean acAvailable;

    // private String hallType;

    private Integer rooms;

    private Boolean carParking;

    private Boolean bikeParking;

    private Boolean diningAvailable;

    private Integer diningCapacity;

    private Boolean generatorAvailable;

    private Boolean liftAvailable;

    private String contactNumber;

    private String whatsappNumber;

    private String coverImageUrl;

    private Boolean bridalRoomAvailable;

    private Boolean cateringKitchenAvailable;

    // private BigDecimal morningAmount;

    // private BigDecimal eveningAmount;

    // private BigDecimal fullDayAmount;

    private String venueType;

    private Integer capacity;

    private BigDecimal startingPrice;

    private Pricing pricing;

    private List<String> amenities;

    public String getVenueType() {
        return venueType;
    }

    public void setVenueType(String venueType) {
        this.venueType = venueType;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(BigDecimal startingPrice) {
        this.startingPrice = startingPrice;
    }

    public Pricing getPricing() {
        return pricing;
    }

    public void setPricing(Pricing pricing) {
        this.pricing = pricing;
    }

    public List<String> getAmenities() {
        return amenities;
    }

    public void setAmenities(List<String> amenities) {
        this.amenities = amenities;
    }

    public Boolean getBridalRoomAvailable() {
        return bridalRoomAvailable;
    }

    public void setBridalRoomAvailable(Boolean bridalRoomAvailable) {
        this.bridalRoomAvailable = bridalRoomAvailable;
    }

    public Boolean getCateringKitchenAvailable() {
        return cateringKitchenAvailable;
    }

    public void setCateringKitchenAvailable(Boolean cateringKitchenAvailable) {
        this.cateringKitchenAvailable = cateringKitchenAvailable;
    }

    // public BigDecimal getMorningAmount() {
    //     return morningAmount;
    // }

    // public void setMorningAmount(BigDecimal morningAmount) {
    //     this.morningAmount = morningAmount;
    // }

    // public BigDecimal getEveningAmount() {
    //     return eveningAmount;
    // }

    // public void setEveningAmount(BigDecimal eveningAmount) {
    //     this.eveningAmount = eveningAmount;
    // }

    // public BigDecimal getFullDayAmount() {
    //     return fullDayAmount;
    // }

    // public void setFullDayAmount(BigDecimal fullDayAmount) {
    //     this.fullDayAmount = fullDayAmount;
    // }

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

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
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

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    // public Integer getCapacityMin() {
    //     return capacityMin;
    // }

    // public void setCapacityMin(Integer capacityMin) {
    //     this.capacityMin = capacityMin;
    // }

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

    public Boolean getAcAvailable() {
        return acAvailable;
    }

    public void setAcAvailable(Boolean acAvailable) {
        this.acAvailable = acAvailable;
    }

    // public String getHallType() {
    //     return hallType;
    // }

    // public void setHallType(String hallType) {
    //     this.hallType = hallType;
    // }

    public Integer getRooms() {
        return rooms;
    }

    public void setRooms(Integer rooms) {
        this.rooms = rooms;
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

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getWhatsappNumber() {
        return whatsappNumber;
    }

    public void setWhatsappNumber(String whatsappNumber) {
        this.whatsappNumber = whatsappNumber;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

}
