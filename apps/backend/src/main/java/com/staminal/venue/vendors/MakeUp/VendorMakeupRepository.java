package com.staminal.venue.vendors.MakeUp;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorMakeupRepository
        extends JpaRepository<VendorMakeupDetails, Long> {

    Optional<VendorMakeupDetails> findByVendorId(Long vendorId);

}
