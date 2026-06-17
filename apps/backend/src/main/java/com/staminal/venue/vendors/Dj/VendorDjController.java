package com.staminal.venue.vendors.Dj;

import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/vendor-dj")
@RequiredArgsConstructor
public class VendorDjController {

    private final VendorDjService vendorDjService;

    @PostMapping
    public VendorDjResponse createDjDetails(
            @RequestBody CreateVendorDjRequest request) {

        return vendorDjService
                .createDjDetails(request);
    }

    @GetMapping("/{vendorId}")
    public VendorDjResponse getDjDetails(
            @PathVariable Long vendorId) {

        return vendorDjService
                .getDjDetails(vendorId);
    }
}