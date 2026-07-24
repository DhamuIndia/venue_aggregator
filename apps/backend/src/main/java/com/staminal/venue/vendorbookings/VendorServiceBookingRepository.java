package com.staminal.venue.vendorbookings;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorServiceBookingRepository extends JpaRepository<VendorServiceBooking, Long> {

    boolean existsByQuote_Id(Long quoteId);

    Optional<VendorServiceBooking> findByLead_Id(Long leadId);

    List<VendorServiceBooking> findByCustomer_IdOrderByEventDateDesc(Long customerId);

    List<VendorServiceBooking> findByVendor_IdOrderByEventDateDesc(Long vendorId);
}
