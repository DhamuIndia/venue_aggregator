package com.staminal.venue.vendors;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.staminal.venue.vendors.Dto.CreateVendorRequest;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorCategoryRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorService {

    private final VendorRepository vendorRepository;
    private final VendorCategoryRepository vendorCategoryRepository;

    public Vendors createVendor(CreateVendorRequest request) {

        Vendors vendor = new Vendors();

        vendor.setUserId(request.getUserId());
        vendor.setVendorName(request.getVendorName());
        vendor.setBusinessName(request.getBusinessName());
        vendor.setDescription(request.getDescription());
        vendor.setCoverImageUrl(request.getCoverImageUrl());
        vendor.setAddressLine(request.getAddressLine());
        vendor.setCity(request.getCity());
        vendor.setArea(request.getArea());
        vendor.setPincode(request.getPincode());
        vendor.setLatitude(request.getLatitude());
        vendor.setLongitude(request.getLongitude());
        vendor.setContactNumber(request.getContactNumber());
        vendor.setWhatsAppNumber(request.getWhatsAppNumber());

        Set<VendorCategory> categories = new HashSet<>(
                vendorCategoryRepository.findAllById(
                        request.getCategoryIds()));

        vendor.setCategories(categories);

        return vendorRepository.save(vendor);
    }

    public List<Vendors> getAllVendors() {
        return vendorRepository.findAll();
    }

    public Vendors getVendorById(Long id) {

        return vendorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));
    }
}
