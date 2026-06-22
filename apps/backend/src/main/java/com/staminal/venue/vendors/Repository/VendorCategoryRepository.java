package com.staminal.venue.vendors.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.vendors.Entity.VendorCategory;

public interface VendorCategoryRepository extends JpaRepository<VendorCategory, Long> {

    
} 
