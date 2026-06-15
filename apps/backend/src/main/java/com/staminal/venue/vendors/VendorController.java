package com.staminal.venue.vendors;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Dto.CreateVendorRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    @PostMapping
    public Vendors createVendor(
            @RequestBody CreateVendorRequest request) {

        return vendorService.createVendor(request);
    }

    @GetMapping
    public List<Vendors> getAllVendors() {

        return vendorService.getAllVendors();
    }

    @GetMapping("/{id}")
    public Vendors getVendorById(
            @PathVariable Long id) {

        return vendorService.getVendorById(id);
    }
}