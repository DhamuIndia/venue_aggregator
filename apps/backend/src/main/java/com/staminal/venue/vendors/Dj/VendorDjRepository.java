package com.staminal.venue.vendors.Dj;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorDjRepository
        extends JpaRepository<VendorDjDetails, Long> {

    Optional<VendorDjDetails> findByVendorId(Long vendorId);
}