package com.staminal.venue.vendors.MakeUp;

import org.springframework.stereotype.Service;

import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorMakeupService {

    private final VendorMakeupRepository vendorMakeUpRepository;
    private final VendorRepository vendorRepository;

    public VendorMakeupResponse createMakeup(
            CreateVendorMakeupRequest request) {

        Vendors vendor = vendorRepository.findById(
                request.getVendorId())
                .orElseThrow(() ->
                        new RuntimeException("Vendor not found"));

        VendorMakeupDetails makeup =
                new VendorMakeupDetails();

        makeup.setVendor(vendor);
        makeup.setExperienceYears(
                request.getExperienceYears());
        makeup.setBridalMakeup(
                request.getBridalMakeup());
        makeup.setHomeService(
                request.getHomeService());
        makeup.setProductsUsed(
                request.getProductsUsed());
        makeup.setStartingPrice(
                request.getStartingPrice());

        VendorMakeupDetails saved =
                vendorMakeUpRepository.save(makeup);

        return mapToResponse(saved);
    }

    public VendorMakeupResponse getMakeup(
            Long vendorId) {

        VendorMakeupDetails makeup =
                vendorMakeUpRepository
                        .findByVendorId(vendorId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Makeup details not found"));

        return mapToResponse(makeup);
    }

    private VendorMakeupResponse mapToResponse(
            VendorMakeupDetails makeup) {

        VendorMakeupResponse response =
                new VendorMakeupResponse();

        response.setId(makeup.getId());
        response.setVendorId(
                makeup.getVendor().getId());
        response.setExperienceYears(
                makeup.getExperienceYears());
        response.setBridalMakeup(
                makeup.getBridalMakeup());
        response.setHomeService(
                makeup.getHomeService());
        response.setProductsUsed(
                makeup.getProductsUsed());
        response.setStartingPrice(
                makeup.getStartingPrice());

        return response;
    }
}