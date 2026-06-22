package com.staminal.venue.vendors.Catering;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorCateringRepository
        extends JpaRepository<VendorCateringDetails, Long> {

    Optional<VendorCateringDetails> findByVendorId(Long vendorId);
}