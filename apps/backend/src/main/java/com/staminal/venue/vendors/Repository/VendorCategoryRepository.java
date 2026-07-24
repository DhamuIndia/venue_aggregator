package com.staminal.venue.vendors.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.vendors.Entity.VendorCategory;

public interface VendorCategoryRepository extends JpaRepository<VendorCategory, Long> {

    List<VendorCategory> findAllByOrderByCategoryNameAsc();
}
