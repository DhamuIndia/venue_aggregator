package com.staminal.venue.vendors.Catering;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.staminal.venue.enums.VendorStatus;

public interface VendorCateringRepository
        extends JpaRepository<VendorCateringDetails, Long> {

    Optional<VendorCateringDetails> findByVendorId(Long vendorId);

    List<VendorCateringDetails> findByStatus(VendorStatus status);
}