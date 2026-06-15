package com.staminal.venue.vendors.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.vendors.Entity.Vendors;

public interface VendorRepository
                extends JpaRepository<Vendors, Long> {

        List<Vendors> findByStatus(VendorStatus status);
}
