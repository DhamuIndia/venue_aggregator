package com.staminal.venue.vendors.Catering;

import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/vendor-catering")
@RequiredArgsConstructor
public class VendorCateringController {

    private final VendorCateringService vendorCateringService;

    @PostMapping
    public VendorCateringResponse create(
            @RequestBody CreateVendorCateringRequest request) {

        return vendorCateringService.create(request);
    }

    @GetMapping("/{vendorId}")
    public VendorCateringResponse getByVendorId(
            @PathVariable Long vendorId) {

        return vendorCateringService.getByVendorId(
                vendorId);
    }
}