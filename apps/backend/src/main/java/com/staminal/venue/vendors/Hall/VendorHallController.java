package com.staminal.venue.vendors.Hall;

import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/vendor-hall")
@RequiredArgsConstructor
public class VendorHallController {

    private final VendorHallService vendorHallService;

    @PostMapping
    public VendorHallResponse createVendorHall(
            @RequestBody CreateVendorHallRequest request) {

        return vendorHallService
                .createVendorHall(request);
    }

    @GetMapping("/{vendorId}")
    public VendorHallResponse getByVendorId(
            @PathVariable Long vendorId) {

        return vendorHallService
                .getByVendorId(vendorId);
    }
}