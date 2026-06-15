package com.staminal.venue.halls.Entity;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import com.staminal.venue.enums.HallType;
import com.staminal.venue.enums.HallStatus;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "halls")
public class Halls {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "owner_user_id", nullable = false)
    private long ownerUserId;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(name = "name")
    private String hallName;

    @Column(nullable = false)
    private String description;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String area;

    @Column(nullable = false)
    private String pincode;

    private Double latitude;

    private Double longitude;

    @Column(name = "capacity_min", nullable = false)
    private int capacityMin;

    @Column(name = "capacity_max", nullable = false)
    private int capacityMax;

    @Column(name = "floors")
    private int floors;

    @Column(name = "ac_available")
    private boolean acAvailable;

    @Enumerated(EnumType.STRING)
    @Column(name = "hall_type")
    private HallType hallType;

    // @Column(name = "review")
    // private String review;

    @Column(name = "ratings")
    private double ratings;

    @Column(name = "rooms")
    private int rooms;

    @Column(name = "car_parking")
    private boolean carParking;

    @Column(name = "bike_parking")
    private boolean bikeParking;

    @Column(name = "dining_available")
    private boolean isDiningAvailable;

    @Column(name = "dining_capacity")
    private int diningCapacity;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "contact_number")
    private String contactNumber;

    @Column(name = "whatsapp_number")
    private String whatsappNumber;

    @Column(name = "generator_available")
    private boolean generatorAvailable;

    @Column(name = "lift_available")
    private boolean liftAvailable;

    @Enumerated(EnumType.STRING)
    private HallStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getHallName() {
        return hallName;
    }

    public void setHallName(String hallName) {
        this.hallName = hallName;
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

    public int getCapacityMin() {
        return capacityMin;
    }

    public void setCapacityMin(int capacityMin) {
        this.capacityMin = capacityMin;
    }

    public int getCapacityMax() {
        return capacityMax;
    }

    public void setCapacityMax(int capacityMax) {
        this.capacityMax = capacityMax;
    }

    public int getFloors() {
        return floors;
    }

    public void setFloors(int floors) {
        this.floors = floors;
    }

    public boolean isAcAvailable() {
        return acAvailable;
    }

    public void setAcAvailable(boolean acAvailable) {
        this.acAvailable = acAvailable;
    }

    public HallType getHallType() {
        return hallType;
    }

    public void setHallType(HallType hallType) {
        this.hallType = hallType;
    }

    public double getRatings() {
        return ratings;
    }

    public void setRatings(double ratings) {
        this.ratings = ratings;
    }

    public int getRooms() {
        return rooms;
    }

    public void setRooms(int rooms) {
        this.rooms = rooms;
    }

    public boolean isCarParking() {
        return carParking;
    }

    public void setCarParking(boolean carParking) {
        this.carParking = carParking;
    }

    public boolean isBikeParking() {
        return bikeParking;
    }

    public void setBikeParking(boolean bikeParking) {
        this.bikeParking = bikeParking;
    }

    public boolean isDiningAvailable() {
        return isDiningAvailable;
    }

    public void setDiningAvailable(boolean isDiningAvailable) {
        this.isDiningAvailable = isDiningAvailable;
    }

    public int getDiningCapacity() {
        return diningCapacity;
    }

    public void setDiningCapacity(int diningCapacity) {
        this.diningCapacity = diningCapacity;
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

    public boolean isGeneratorAvailable() {
        return generatorAvailable;
    }

    public void setGeneratorAvailable(boolean generatorAvailable) {
        this.generatorAvailable = generatorAvailable;
    }

    public boolean isLiftAvailable() {
        return liftAvailable;
    }

    public void setLiftAvailable(boolean liftAvailable) {
        this.liftAvailable = liftAvailable;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    

}
