package com.staminal.venue.vendors.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.vendors.Dto.VendorPackageListResponse;
import com.staminal.venue.vendors.Dto.VendorPackageResponse;
import com.staminal.venue.vendors.Dto.VendorPackageUpsertRequest;
import com.staminal.venue.vendors.Service.VendorPackageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/vendor/packages")
@RequiredArgsConstructor
public class VendorPackageV1Controller {

    private final VendorPackageService vendorPackageService;

    @GetMapping
    public VendorPackageListResponse getPackages(Authentication authentication) {
        return vendorPackageService.getMyPackages(authentication);
    }

    @PostMapping
    public VendorPackageResponse createPackage(
            @RequestBody VendorPackageUpsertRequest request,
            Authentication authentication) {
        return vendorPackageService.createMyPackage(request, authentication);
    }

    @PutMapping("/{packageId}")
    public VendorPackageResponse updatePackage(
            @PathVariable String packageId,
            @RequestBody VendorPackageUpsertRequest request,
            Authentication authentication) {
        return vendorPackageService.updateMyPackage(packageId, request, authentication);
    }

    @DeleteMapping("/{packageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePackage(@PathVariable String packageId, Authentication authentication) {
        vendorPackageService.deleteMyPackage(packageId, authentication);
    }
}
