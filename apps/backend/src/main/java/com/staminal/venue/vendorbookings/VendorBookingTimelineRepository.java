package com.staminal.venue.vendorbookings;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorBookingTimelineRepository extends JpaRepository<VendorBookingTimeline, Long> {
    List<VendorBookingTimeline> findByBooking_IdOrderByCreatedAtDesc(Long bookingId);
}
