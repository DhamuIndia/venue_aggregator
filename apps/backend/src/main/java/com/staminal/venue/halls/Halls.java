package com.staminal.venue.halls;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "halls")
public class Halls {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "owner_user_id", nullable = false)
    private long ownerUserId;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(name = "hall_name")
    private String hallName;

    @Column(nullable = false)
    private String description;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(nullable = false)
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

    @Column(name = "hallFloors")
    private int floors;

    @Column(name = "ac_nonAc")
    private boolean acAvailable;

    @Column(name = "hall_type")
    private String hallType;

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

    @Column(name = "isDiningAvailable")
    private boolean isDiningAvailable;

    @Column(name = "dining_capacity")
    private int diningCapacity;

    @Column(name = "amount")
    private double amount;

    @Column(name = "contact_number")
    private String contactNumber;

    @Column(name = "whatsApp_number")
    private String whatsappNumber;

    @Column(name = "generator_available")
    private boolean generatorAvailable;

    @Column(name = "lift_available")
    private boolean liftAvailable;

    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

}
