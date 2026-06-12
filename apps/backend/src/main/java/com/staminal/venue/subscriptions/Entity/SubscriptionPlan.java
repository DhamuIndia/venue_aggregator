package com.staminal.venue.subscriptions.Entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String name;

    @Column(name = "price_amount")
    private double priceAmount;

    private String currency;

    @Column(name = "billing_period")
    private String billingPeriod;

    @Column(name = "is_active")
    private boolean isActive;

    @Column(name = "created_at")
    private Instant createdAt;
}
