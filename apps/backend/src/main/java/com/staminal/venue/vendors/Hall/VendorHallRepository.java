package com.staminal.venue.vendors.Hall;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorHallRepository
        extends JpaRepository<VendorHallDetails, Long> {

    Optional<VendorHallDetails> findByVendorId(Long vendorId);

}
