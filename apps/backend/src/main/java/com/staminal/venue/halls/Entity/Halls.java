package com.staminal.venue.halls.Entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.users.Entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "halls")
public class Halls {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name="owner_user_id")
    private User ownerUserId;

    @Column(name="owner_name")
    private String ownerName;

    private String name;

    private String description;

    @Column(name="cover_image_url")
    private String coverImageUrl;

    @Column(name="address_line")
    private String addressLine;

    private String city;

    private String area;

    private String pincode;

    private Double latitude;

    private Double longitude;

    @Column(name="capacity_min")
    private Integer capacityMin;

     @Column(name="capacity_max")
    private Integer capacityMax;

    private Integer floors;

    @Column(name="ac_available")
    private Boolean acAvailable;

    @Column(name="hall_type")
    private String hallType;

    private Double ratings;

    private Integer rooms;

    @Column(name="car_parking")
    private Boolean carParking;

    @Column(name="bike_parking")
    private Boolean bikeParking;

    @Column(name="dining_available")
    private Boolean diningAvailable;

    @Column(name="dining_capacity")
    private Integer diningCapacity;

    @Column(name="generator_available")
    private Boolean generatorAvailable;

    @Column(name="lift_available")
    private Boolean liftAvailable;

    private BigDecimal amount;

    @Column(name="contact_number")
    private String contactNumber;

    @Column(name="whatsapp_number")
    private String whatsappNumber;

    private HallStatus status;

    @Column(name="rejection_reason")
    private String rejectionReason;

    @Column(name="created_at")
    private LocalDateTime createdAt;

    @Column(name="updated_at")
    private LocalDateTime updatedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public User getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(User ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
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

    public Boolean getAcAvailable() {
        return acAvailable;
    }

    public void setAcAvailable(Boolean acAvailable) {
        this.acAvailable = acAvailable;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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

    public HallStatus getStatus() {
        return status;
    }

    public void setStatus(HallStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

}
