package com.staminal.venue.vendors.Controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import com.staminal.venue.vendors.Dto.UpdateVendorRequest;

import com.staminal.venue.vendors.Dto.CreateVendorRequest;
import com.staminal.venue.vendors.Dto.VendorLoginRequest;
import com.staminal.venue.vendors.Dto.VendorLoginResponse;
import com.staminal.venue.vendors.Dto.VendorResponse;
import com.staminal.venue.vendors.Service.VendorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/vendor")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    @PostMapping
    public VendorResponse createVendor(Principal principal,@RequestBody CreateVendorRequest request) {
        return vendorService.createVendor(principal.getName(),request);
    }

    @GetMapping
    public List<VendorResponse> getAllVendors(Principal principal) {
        System.out.println("Vendor profile API called");
        System.out.println("Principal = " + principal);
        return vendorService.getAllVendors();
    }

    @GetMapping("/{id}")
    public VendorResponse getVendorById(@PathVariable Long id) {
        return vendorService.getVendorById(id);
    }

    @GetMapping("/category/{categoryName}")
    public List<VendorResponse> getVendorsByCategory(@PathVariable String categoryName) {
        return vendorService.getVendorsByCategory(categoryName);
    }

    @PostMapping("/login")
    public VendorLoginResponse login(@RequestBody VendorLoginRequest request) {
        return vendorService.login(request);
    }

    @GetMapping("/profile")
    public VendorResponse getProfile(Principal principal) {
         System.out.println("===== INSIDE PROFILE API =====");
    System.out.println(principal);
        return vendorService.getProfile(principal.getName());
    }

    @PutMapping("/profile")
    public VendorResponse updateProfile(
            Principal principal,
            @RequestBody UpdateVendorRequest request) {

        return vendorService.updateProfile(
                principal.getName(),
                request);
    }

    @PostMapping("/profile/submit")
    public VendorResponse submitProfile(
            Principal principal) {

        return vendorService.submitProfile(
                principal.getName());

    }
}