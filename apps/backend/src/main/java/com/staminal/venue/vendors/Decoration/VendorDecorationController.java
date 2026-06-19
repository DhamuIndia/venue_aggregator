package com.staminal.venue.vendors.Decoration;

import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/vendor-decoration")
@RequiredArgsConstructor
public class VendorDecorationController {

    private final VendorDecorationService vendorDecorationService;

    @PostMapping
    public VendorDecorationResponse createDecoration(
            @RequestBody
            CreateVendorDecorationRequest request) {

        return vendorDecorationService
                .createDecoration(request);
    }

    @GetMapping("/{vendorId}")
    public VendorDecorationResponse getDecorationByVendorId(
            @PathVariable Long vendorId) {

        return vendorDecorationService
                .getDecorationByVendorId(vendorId);
    }
}