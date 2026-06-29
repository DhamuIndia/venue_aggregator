package com.staminal.venue.vendors.Controller;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.vendors.Dto.PublicVendorListResponse;
import com.staminal.venue.vendors.Dto.PublicVendorResponse;
import com.staminal.venue.vendors.Service.PublicVendorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/public/vendors")
public class PublicVendorController {

    private final PublicVendorService publicVendorService;

    @GetMapping
    public PublicVendorListResponse searchPublicVendors(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean verified,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return publicVendorService.searchPublicVendors(q, city, area, category, maxPrice, verified, sort, page, size);
    }

    @GetMapping("/{vendorId}")
    public PublicVendorResponse getPublicVendor(@PathVariable String vendorId) {
        return publicVendorService.getPublicVendor(vendorId);
    }
}
