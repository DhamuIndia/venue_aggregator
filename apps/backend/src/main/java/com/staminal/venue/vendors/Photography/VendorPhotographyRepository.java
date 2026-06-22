package com.staminal.venue.vendors.Photography;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorPhotographyRepository
        extends JpaRepository<VendorPhotographyDetails, Long> {

    Optional<VendorPhotographyDetails> findByVendorId(Long vendorId);

}