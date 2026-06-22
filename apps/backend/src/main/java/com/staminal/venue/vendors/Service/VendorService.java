package com.staminal.venue.vendors.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.vendors.Dto.CreateVendorRequest;
import com.staminal.venue.vendors.Dto.VendorResponse;
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

    public VendorResponse createVendor(CreateVendorRequest request) {

        Vendors vendor = new Vendors();
        vendor.setVendorName(request.getVendorName());
        vendor.setBusinessName(request.getBusinessName());
        vendor.setDescription(request.getDescription());
        vendor.setCoverImageUrl(request.getCoverImageUrl());
        vendor.setAddressLine(request.getAddressLine());
        vendor.setEmail(request.getEmail());
        vendor.setCity(request.getCity());
        vendor.setArea(request.getArea());
        vendor.setPincode(request.getPincode());
        vendor.setLatitude(request.getLatitude());
        vendor.setLongitude(request.getLongitude());
        vendor.setContactNumber(request.getContactNumber());
        vendor.setWhatsAppNumber(request.getWhatsAppNumber());
        vendor.setStatus(VendorStatus.PENDING);
        vendor.setCreatedAt(Instant.now());
        vendor.setUpdatedAt(Instant.now());

        Set<VendorCategory> categories = new HashSet<>(
                vendorCategoryRepository.findAllById(
                        request.getCategoryIds()));

        vendor.setCategories(categories);

        Vendors savedVendor = vendorRepository.save(vendor);

        return mapToResponse(savedVendor);
    }

    @Transactional(readOnly = true)
    public List<VendorResponse> getAllVendors() {

        return vendorRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private VendorResponse mapToResponse(Vendors vendor) {

        VendorResponse response = new VendorResponse();

        response.setId(vendor.getId());
        response.setVendorName(vendor.getVendorName());
        response.setBusinessName(vendor.getBusinessName());
        response.setDescription(vendor.getDescription());
        response.setCoverImageUrl(vendor.getCoverImageUrl());
        response.setCity(vendor.getCity());
        response.setArea(vendor.getArea());
        response.setContactNumber(vendor.getContactNumber());
        response.setWhatsAppNumber(vendor.getWhatsAppNumber());
        response.setStatus(vendor.getStatus().name());

        response.setCategories(
                vendor.getCategories()
                        .stream()
                        .map(VendorCategory::getCategoryName)
                        .collect(Collectors.toSet()));

        return response;
    }

    @Transactional(readOnly = true)
    public VendorResponse getVendorById(Long id) {

        Vendors vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        return mapToResponse(vendor);
    }

    public List<VendorResponse> getVendorsByCategory(
            String categoryName) {

        List<Vendors> vendors = vendorRepository.findByCategories_CategoryName(
                categoryName);

        return vendors.stream()
                .map(this::mapToResponse)
                .toList();
    }
}
