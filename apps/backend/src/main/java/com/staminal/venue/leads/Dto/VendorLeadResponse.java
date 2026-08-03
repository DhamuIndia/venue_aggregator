package com.staminal.venue.leads.Dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.staminal.venue.enums.VendorLeadStatus;
import com.staminal.venue.enums.PreferredContactChannel;

public class VendorLeadResponse {

    private Long id;

    private String leadReference;

    private String vendorId;

    private String vendorName;

    private String customerId;

    private String customerName;

    private String customerPhone;

    private String customerEmail;

    private Long requirementId;

    private String source;

    private boolean contactDetailsShared;

    private PreferredContactChannel preferredContactChannel;

    private String service;

    private String eventType;

    private LocalDate eventDate;

    private String location;

    private BigDecimal budget;

    private String notes;

    private String declineReason;

    private VendorLeadStatus status;

    private Instant createdAt;

    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLeadReference() {
        return leadReference;
    }

    public void setLeadReference(String leadReference) {
        this.leadReference = leadReference;
    }

    public String getVendorId() {
        return vendorId;
    }

    public void setVendorId(String vendorId) {
        this.vendorId = vendorId;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public Long getRequirementId() {
        return requirementId;
    }

    public void setRequirementId(Long requirementId) {
        this.requirementId = requirementId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isContactDetailsShared() {
        return contactDetailsShared;
    }

    public void setContactDetailsShared(boolean contactDetailsShared) {
        this.contactDetailsShared = contactDetailsShared;
    }

    public PreferredContactChannel getPreferredContactChannel() {
        return preferredContactChannel;
    }

    public void setPreferredContactChannel(PreferredContactChannel preferredContactChannel) {
        this.preferredContactChannel = preferredContactChannel;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
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

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(String declineReason) {
        this.declineReason = declineReason;
    }

    public VendorLeadStatus getStatus() {
        return status;
    }

    public void setStatus(VendorLeadStatus status) {
        this.status = status;
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
