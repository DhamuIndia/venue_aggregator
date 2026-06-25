package com.staminal.venue.enquiries;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.enquiries.dto.CreateEnquiryRequest;
import com.staminal.venue.enquiries.dto.EnquiryResponse;
import com.staminal.venue.enquiries.dto.UpdateEnquiryStatusRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class EnquiryController {

    private final EnquiryService enquiryService;

    @PostMapping("/public/enquiries")
    public ResponseEntity<EnquiryResponse> createEnquiry(
            @Valid @RequestBody CreateEnquiryRequest request,
            Authentication authentication) {
        EnquiryResponse response = enquiryService.createHallEnquiry(request, authentication);
        return ResponseEntity
                .created(URI.create("/api/v1/customer/enquiries/" + response.id()))
                .body(response);
    }

    @GetMapping("/customer/enquiries")
    public List<EnquiryResponse> getCustomerEnquiries(Authentication authentication) {
        return enquiryService.getCustomerEnquiries(authentication);
    }

    @GetMapping("/customer/enquiries/{enquiryId}")
    public EnquiryResponse getCustomerEnquiry(
            @PathVariable String enquiryId,
            Authentication authentication) {
        return enquiryService.getCustomerEnquiry(enquiryId, authentication);
    }

    @GetMapping("/owner/halls/{hallId}/enquiries")
    public List<EnquiryResponse> getOwnerHallEnquiries(
            @PathVariable String hallId,
            Authentication authentication) {
        return enquiryService.getOwnerHallEnquiries(hallId, authentication);
    }

    @PatchMapping("/owner/enquiries/{enquiryId}/status")
    public EnquiryResponse updateOwnerEnquiryStatus(
            @PathVariable String enquiryId,
            @Valid @RequestBody UpdateEnquiryStatusRequest request,
            Authentication authentication) {
        return enquiryService.updateOwnerEnquiryStatus(enquiryId, request, authentication);
    }
}
