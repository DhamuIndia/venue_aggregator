package com.staminal.venue.vendors.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.vendors.Entity.VendorMedia;

public interface VendorMediaRepository
        extends JpaRepository<VendorMedia, Long> {

    List<VendorMedia> findByVendor_Id(Long vendorId);

    List<VendorMedia> findByVendor_User_Id(Long userId);

    Optional<VendorMedia> findByIdAndVendor_User_Id(Long id, Long userId);

}
