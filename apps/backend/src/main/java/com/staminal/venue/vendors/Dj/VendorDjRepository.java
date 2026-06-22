package com.staminal.venue.vendors.Dj;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.enums.VendorStatus;

public interface VendorDjRepository
        extends JpaRepository<VendorDjDetails, Long> {

    Optional<VendorDjDetails> findByVendor_Id(Long vendorId);

    List<VendorDjDetails> findByStatus(VendorStatus status);
}