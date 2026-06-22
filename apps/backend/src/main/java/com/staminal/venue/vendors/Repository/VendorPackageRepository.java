package com.staminal.venue.vendors.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.vendors.Entity.VendorPackage;

public interface VendorPackageRepository
        extends JpaRepository<VendorPackage, Long> {

    List<VendorPackage> findByVendor_Id(Long vendorId);

}
