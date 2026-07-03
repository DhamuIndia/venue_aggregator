package com.staminal.venue.reports;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class ReportsController {

    private final ReportsService reportsService;

    @GetMapping("/admin/reports/summary")
    public AdminReportResponse getAdminSummary(Authentication authentication) {
        return reportsService.getAdminSummary(authentication);
    }

    @GetMapping("/owner/halls/{hallId}/reports/summary")
    public OwnerReportResponse getOwnerHallSummary(
            @PathVariable String hallId,
            Authentication authentication) {
        return reportsService.getOwnerHallSummary(hallId, authentication);
    }

    @GetMapping("/vendor/reports/summary")
    public VendorReportResponse getVendorSummary(
            @RequestParam(required = false) String vendorId,
            Authentication authentication) {
        return reportsService.getVendorSummary(vendorId, authentication);
    }
}
