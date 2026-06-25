package com.staminal.venue.vendors.Catering;

import java.math.BigDecimal;

import com.staminal.venue.enums.CateringServiceType;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.vendors.Entity.Vendors;

import jakarta.persistence.*;

@Entity
@Table(name = "vendor_catering_details")
public class VendorCateringDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendors vendor;

    @Column(name = "veg_available")
    private Boolean vegAvailable;

    @Column(name = "non_veg_available")
    private Boolean nonVegAvailable;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type")
    private CateringServiceType serviceType;

    @Column(name = "min_guest_count")
    private Integer minGuestCount;

    @Column(name = "max_guest_count")
    private Integer maxGuestCount;

    @Column(name = "live_counter_available")
    private Boolean liveCounterAvailable;

    @Column(name = "starting_price_per_plate")
    private BigDecimal startingPricePerPlate;

    @Enumerated(EnumType.STRING)
    private VendorStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    public VendorStatus getStatus() {
        return status;
    }

    public void setStatus(VendorStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Vendors getVendor() {
        return vendor;
    }

    public void setVendor(Vendors vendor) {
        this.vendor = vendor;
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