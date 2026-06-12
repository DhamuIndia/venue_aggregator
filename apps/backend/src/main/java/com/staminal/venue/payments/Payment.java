package com.staminal.venue.payments;

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

    private double amount;

    private String currency;

    private String status;

    @Column(name = "razorpay_payment_id")
    private String razorPayPaymentId;

    @Column(name = "razorpay_order_id")
    private String razorPayOrderID;

    @Column(name = "created_at")
    private Instant createdAt;
}
