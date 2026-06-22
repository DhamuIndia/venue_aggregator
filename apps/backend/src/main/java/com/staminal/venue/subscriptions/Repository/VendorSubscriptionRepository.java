package com.staminal.venue.subscriptions.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.subscriptions.Entity.VendorSubscription;

public interface VendorSubscriptionRepository extends JpaRepository<VendorSubscription, Long> {

}
