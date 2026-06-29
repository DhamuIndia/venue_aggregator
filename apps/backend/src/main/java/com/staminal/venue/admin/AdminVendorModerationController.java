package com.staminal.venue.admin;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/vendors")
public class AdminVendorModerationController {

    private final AdminVendorModerationService adminVendorModerationService;

    @GetMapping
    public AdminVendorListResponse getVendors(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return adminVendorModerationService.getVendors(status, page, size);
    }

    @PatchMapping("/{vendorId}/review")
    public AdminVendorResponse reviewVendor(
            @PathVariable String vendorId,
            @RequestBody AdminReviewRequest request,
            Authentication authentication) {
        return adminVendorModerationService.reviewVendor(vendorId, request, authentication);
    }
}
