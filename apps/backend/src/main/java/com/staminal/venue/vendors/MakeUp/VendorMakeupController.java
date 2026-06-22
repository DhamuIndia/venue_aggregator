package com.staminal.venue.vendors.MakeUp;

import org.springframework.web.bind.annotation.*;


import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/vendor-makeup")
@RequiredArgsConstructor
public class VendorMakeupController {

    private final VendorMakeupService vendorMakeupService;

    @PostMapping
    public VendorMakeupResponse createMakeup(
            @RequestBody CreateVendorMakeupRequest request) {

        return vendorMakeupService
                .createMakeup(request);
    }

    @GetMapping("/{vendorId}")
    public VendorMakeupResponse getMakeup(
            @PathVariable Long vendorId) {

        return vendorMakeupService
                .getMakeup(vendorId);
    }
}
