package com.staminal.venue.requirements;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import com.staminal.venue.enums.CustomerRequirementStatus;
import com.staminal.venue.enums.PreferredContactChannel;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.vendors.Entity.VendorCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_requirements")
public class CustomerRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_user_id", nullable = false)
    private User customer;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(nullable = false, length = 255)
    private String location;

    @Column(length = 120)
    private String city;

    @Column(length = 16)
    private String pincode;

    @Column(name = "budget_min", precision = 12, scale = 2)
    private BigDecimal budgetMin;

    @Column(name = "budget_max", precision = 12, scale = 2)
    private BigDecimal budgetMax;

    @Column(name = "guest_count")
    private Integer guestCount;

    @Column(length = 2000)
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_contact_channel", nullable = false, length = 30)
    private PreferredContactChannel preferredContactChannel;

    @Column(name = "share_contact_details", nullable = false)
    private boolean shareContactDetails;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CustomerRequirementStatus status;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "requirement_services",
            joinColumns = @JoinColumn(name = "requirement_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<VendorCategory> serviceCategories = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (preferredContactChannel == null) {
            preferredContactChannel = PreferredContactChannel.IN_APP;
        }
        if (status == null) {
            status = CustomerRequirementStatus.OPEN;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User customer) {
        this.customer = customer;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public BigDecimal getBudgetMin() {
        return budgetMin;
    }

    public void setBudgetMin(BigDecimal budgetMin) {
        this.budgetMin = budgetMin;
    }

    public BigDecimal getBudgetMax() {
        return budgetMax;
    }

    public void setBudgetMax(BigDecimal budgetMax) {
        this.budgetMax = budgetMax;
    }

    public Integer getGuestCount() {
        return guestCount;
    }

    public void setGuestCount(Integer guestCount) {
        this.guestCount = guestCount;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public PreferredContactChannel getPreferredContactChannel() {
        return preferredContactChannel;
    }

    public void setPreferredContactChannel(PreferredContactChannel preferredContactChannel) {
        this.preferredContactChannel = preferredContactChannel;
    }

    public boolean isShareContactDetails() {
        return shareContactDetails;
    }

    public void setShareContactDetails(boolean shareContactDetails) {
        this.shareContactDetails = shareContactDetails;
    }

    public CustomerRequirementStatus getStatus() {
        return status;
    }

    public void setStatus(CustomerRequirementStatus status) {
        this.status = status;
    }

    public Set<VendorCategory> getServiceCategories() {
        return serviceCategories;
    }

    public void setServiceCategories(Set<VendorCategory> serviceCategories) {
        this.serviceCategories = serviceCategories == null ? new HashSet<>() : serviceCategories;
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
