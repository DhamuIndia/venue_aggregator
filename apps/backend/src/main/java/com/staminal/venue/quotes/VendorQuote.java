package com.staminal.venue.quotes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.staminal.venue.enums.VendorQuoteStatus;
import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.vendors.Entity.Vendors;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "vendor_quotes")
public class VendorQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_lead_id", nullable = false, unique = true)
    private VendorLead lead;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendors vendor;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "package_name", nullable = false, length = 160)
    private String packageName;

    @Column(name = "service_description", nullable = false, length = 2000)
    private String serviceDescription;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "vendor_quote_inclusions", joinColumns = @JoinColumn(name = "quote_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "inclusion", nullable = false, length = 300)
    private List<String> inclusions = new ArrayList<>();

    @Column(name = "additional_charges", nullable = false, precision = 12, scale = 2)
    private BigDecimal additionalCharges;

    @Column(name = "additional_charges_description", length = 1000)
    private String additionalChargesDescription;

    @Column(length = 2000)
    private String notes;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VendorQuoteStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (additionalCharges == null) {
            additionalCharges = BigDecimal.ZERO;
        }
        if (status == null) {
            status = VendorQuoteStatus.SENT;
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

    public VendorLead getLead() {
        return lead;
    }

    public void setLead(VendorLead lead) {
        this.lead = lead;
    }

    public Vendors getVendor() {
        return vendor;
    }

    public void setVendor(Vendors vendor) {
        this.vendor = vendor;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getServiceDescription() {
        return serviceDescription;
    }

    public void setServiceDescription(String serviceDescription) {
        this.serviceDescription = serviceDescription;
    }

    public List<String> getInclusions() {
        return inclusions;
    }

    public void setInclusions(List<String> inclusions) {
        this.inclusions = inclusions == null ? new ArrayList<>() : new ArrayList<>(inclusions);
    }

    public BigDecimal getAdditionalCharges() {
        return additionalCharges;
    }

    public void setAdditionalCharges(BigDecimal additionalCharges) {
        this.additionalCharges = additionalCharges;
    }

    public String getAdditionalChargesDescription() {
        return additionalChargesDescription;
    }

    public void setAdditionalChargesDescription(String additionalChargesDescription) {
        this.additionalChargesDescription = additionalChargesDescription;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate validUntil) {
        this.validUntil = validUntil;
    }

    public VendorQuoteStatus getStatus() {
        return status;
    }

    public void setStatus(VendorQuoteStatus status) {
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
