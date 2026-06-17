package com.staminal.venue.vendors.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.vendors.Entity.VendorMedia;

public interface VendorMediaRepository
        extends JpaRepository<VendorMedia, Long> {

    List<VendorMedia> findByVendor_Id(Long vendorId);

}
