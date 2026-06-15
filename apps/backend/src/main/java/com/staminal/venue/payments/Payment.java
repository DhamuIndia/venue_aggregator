package com.staminal.venue.payments;

import java.math.BigDecimal;
import java.time.Instant;
import com.staminal.venue.subscriptions.Entity.VendorSubscription;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "vendor_subscription_id")
    private VendorSubscription vendorSubscription;

    private BigDecimal amount;

    private String currency;

    private String status;

    @Column(name = "razorpay_payment_id")
    private String razorPayPaymentId;

    @Column(name = "razorpay_order_id")
    private String razorPayOrderID;

    @Column(name = "created_at")
    private Instant createdAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public VendorSubscription getVendorSubscription() {
        return vendorSubscription;
    }

    public void setVendorSubscription(VendorSubscription vendorSubscription) {
        this.vendorSubscription = vendorSubscription;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRazorPayPaymentId() {
        return razorPayPaymentId;
    }

    public void setRazorPayPaymentId(String razorPayPaymentId) {
        this.razorPayPaymentId = razorPayPaymentId;
    }

    public String getRazorPayOrderID() {
        return razorPayOrderID;
    }

    public void setRazorPayOrderID(String razorPayOrderID) {
        this.razorPayOrderID = razorPayOrderID;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    
}
