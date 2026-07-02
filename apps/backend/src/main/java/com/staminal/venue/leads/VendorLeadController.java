package com.staminal.venue.leads;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.leads.Dto.CreateVendorLeadRequest;
import com.staminal.venue.leads.Dto.UpdateVendorLeadStatusRequest;
import com.staminal.venue.leads.Dto.VendorLeadResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class VendorLeadController {

    private final VendorLeadService vendorLeadService;

    @PostMapping("/public/vendor-leads")
    public VendorLeadResponse createLead(
            @RequestBody CreateVendorLeadRequest request,
            Authentication authentication) {

        return vendorLeadService.createLead(request, authentication);
    }

    @GetMapping("/vendor/leads")
    public List<VendorLeadResponse> getMyLeads(Authentication authentication) {
        return vendorLeadService.getMyLeads(authentication);
    }

    @GetMapping("/vendor/leads/{leadId}")
    public VendorLeadResponse getLead(
            @PathVariable Long leadId,
            Authentication authentication) {

        return vendorLeadService.getLead(leadId, authentication);
    }

    @PatchMapping("/vendor/leads/{leadId}/status")
    public VendorLeadResponse updateStatus(
            @PathVariable Long leadId,
            @RequestBody UpdateVendorLeadStatusRequest request,
            Authentication authentication) {

        return vendorLeadService.updateStatus(
                leadId,
                request,
                authentication);
    }
}