package com.staminal.venue.vendors.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.vendors.Entity.Vendors;

public interface VendorRepository
        extends JpaRepository<Vendors, Long> {
}
