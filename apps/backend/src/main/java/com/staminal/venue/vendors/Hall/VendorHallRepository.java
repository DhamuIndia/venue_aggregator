package com.staminal.venue.vendors.Hall;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.enums.VendorStatus;

public interface VendorHallRepository extends JpaRepository<VendorHallDetails, Long> {

    Optional<VendorHallDetails> findByVendorId(Long vendorId);

    List<VendorHallDetails> findByStatus(VendorStatus status);

}
