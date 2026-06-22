package com.staminal.venue.vendors.Controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;

import com.staminal.venue.vendors.Dto.CreateVendorRequest;
import com.staminal.venue.vendors.Dto.VendorResponse;
import com.staminal.venue.vendors.Service.VendorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    @PostMapping
    public VendorResponse createVendor(
            @RequestBody CreateVendorRequest request) {

        return vendorService.createVendor(request);
    }

    @GetMapping
    public List<VendorResponse> getAllVendors() {

        return vendorService.getAllVendors();
    }

    @GetMapping("/{id}")
    public VendorResponse getVendorById(
            @PathVariable Long id) {

        return vendorService.getVendorById(id);
    }

    @GetMapping("/category/{categoryName}")
    public List<VendorResponse> getVendorsByCategory(
            @PathVariable String categoryName) {

        return vendorService.getVendorsByCategory(
                categoryName);
    }
}