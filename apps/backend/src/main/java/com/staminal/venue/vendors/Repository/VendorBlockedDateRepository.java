package com.staminal.venue.vendors.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.vendors.Entity.VendorBlockedDate;

public interface VendorBlockedDateRepository
        extends JpaRepository<VendorBlockedDate, Long> {

    List<VendorBlockedDate> findByVendor_Id(Long vendorId);

}
