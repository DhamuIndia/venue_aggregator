package com.staminal.venue.quotes;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorQuoteRepository extends JpaRepository<VendorQuote, Long> {

    Optional<VendorQuote> findByLead_Id(Long leadId);

    List<VendorQuote> findByVendor_IdOrderByUpdatedAtDesc(Long vendorId);

    List<VendorQuote> findByLead_Customer_IdOrderByUpdatedAtDesc(Long customerId);
}
