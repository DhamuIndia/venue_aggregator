package com.staminal.venue.vendors.Service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.staminal.venue.vendors.Dto.CreateVendorPackageRequest;
import com.staminal.venue.vendors.Dto.VendorPackageResponse;
import com.staminal.venue.vendors.Entity.VendorPackage;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorPackageRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorPackageService {

    private final VendorPackageRepository vendorPackageRepository;
    private final VendorRepository vendorRepository;

    public VendorPackageResponse createPackage(
            CreateVendorPackageRequest request) {

        Vendors vendor = vendorRepository.findById(
                request.getVendorId())
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        VendorPackage vendorPackage = new VendorPackage();

        vendorPackage.setVendor(vendor);
        vendorPackage.setPackageName(request.getPackageName());
        vendorPackage.setDescription(request.getDescription());
        vendorPackage.setPrice(request.getPrice());
        vendorPackage.setCreatedAt(Instant.now());

        VendorPackage savedVendorPackage = vendorPackageRepository.save(vendorPackage);

        VendorPackageResponse vendorPackageResponse = new VendorPackageResponse();
        vendorPackageResponse.setId(savedVendorPackage.getId());
        vendorPackageResponse.setDescription(savedVendorPackage.getDescription());
        vendorPackageResponse.setPackageName(savedVendorPackage.getPackageName());
        vendorPackageResponse.setPrice(savedVendorPackage.getPrice());
        return vendorPackageResponse;
    }

    public List<VendorPackageResponse> getPackages(Long vendorId) {

        List<VendorPackage> packages = vendorPackageRepository.findByVendor_Id(vendorId);

        return packages.stream()
                .map(vendorPackage -> {

                    VendorPackageResponse response = new VendorPackageResponse();

                    response.setId(vendorPackage.getId());
                    response.setPackageName(
                            vendorPackage.getPackageName());
                    response.setDescription(
                            vendorPackage.getDescription());
                    response.setPrice(
                            vendorPackage.getPrice());

                    return response;
                })
                .toList();
    }
}