package com.staminal.venue.vendors.Controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import com.staminal.venue.vendors.Dto.CreateVendorPackageRequest;
import com.staminal.venue.vendors.Dto.VendorPackageResponse;
import com.staminal.venue.vendors.Service.VendorPackageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/vendor-packages")
@RequiredArgsConstructor
public class VendorPackageController {

    private final VendorPackageService vendorPackageService;

    @PostMapping
    public VendorPackageResponse  createPackage(
            @RequestBody CreateVendorPackageRequest request) {

        return vendorPackageService.createPackage(request);
    }

    @GetMapping("/{vendorId}")
    public List<VendorPackageResponse> getPackages(
            @PathVariable Long vendorId) {

        return vendorPackageService.getPackages(vendorId);
    }
}