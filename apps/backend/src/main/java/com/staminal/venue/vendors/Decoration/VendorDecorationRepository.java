package com.staminal.venue.vendors.Decoration;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorDecorationRepository
        extends JpaRepository<VendorDecorationDetails, Long> {

    Optional<VendorDecorationDetails> findByVendorId(Long vendorId);
}
