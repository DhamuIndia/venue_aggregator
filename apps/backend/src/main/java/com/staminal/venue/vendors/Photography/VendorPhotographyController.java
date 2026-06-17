package com.staminal.venue.vendors.Photography;

import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/vendor-photography")
@RequiredArgsConstructor
public class VendorPhotographyController {

    private final VendorPhotographyService vendorPhotographyService;

    @PostMapping
    public VendorPhotographyResponse createPhotography(
            @RequestBody CreateVendorPhotographyRequest request) {

        return vendorPhotographyService
                .createPhotography(request);
    }

    @GetMapping("/{vendorId}")
    public VendorPhotographyResponse getPhotography(
            @PathVariable Long vendorId) {

        return vendorPhotographyService
                .getPhotography(vendorId);
    }
}
