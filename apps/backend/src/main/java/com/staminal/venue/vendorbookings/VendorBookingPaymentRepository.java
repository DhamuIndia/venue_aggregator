package com.staminal.venue.vendorbookings;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorBookingPaymentRepository extends JpaRepository<VendorBookingPayment, Long> {
    List<VendorBookingPayment> findByBooking_IdOrderByCreatedAtDesc(Long bookingId);
    Optional<VendorBookingPayment> findByProviderOrderIdAndBooking_Id(String providerOrderId, Long bookingId);
    Optional<VendorBookingPayment> findFirstByBooking_IdAndPaymentTypeAndStatusOrderByCreatedAtDesc(
            Long bookingId,
            String paymentType,
            VendorBookingPaymentStatus status);
}
