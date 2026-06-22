package com.staminal.venue.subscriptions.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.staminal.venue.subscriptions.Entity.SubscriptionPlan;

public interface SubscriptionPlanRepository
        extends JpaRepository<SubscriptionPlan, Long> {

}