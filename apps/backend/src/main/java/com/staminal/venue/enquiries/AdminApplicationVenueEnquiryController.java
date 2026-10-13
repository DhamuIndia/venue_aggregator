package com.staminal.venue.enquiries;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.staminal.venue.enquiries.dto.ApplicationVenueEnquiryPage;
import com.staminal.venue.enquiries.dto.EnquiryResponse;
import com.staminal.venue.enquiries.dto.UpdateApplicationVenueEnquiryRequest;
import com.staminal.venue.enums.EnquiryStatus;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/application-venue-enquiries")
@RequiredArgsConstructor
public class AdminApplicationVenueEnquiryController {
    private final ApplicationVenueEnquiryService service;

    @GetMapping
    public ResponseEntity<ApplicationVenueEnquiryPage> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) EnquiryStatus status,
            Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(page, size, status, authentication));
    }

    @PutMapping("/{enquiryId}")
    public ResponseEntity<EnquiryResponse> update(@PathVariable String enquiryId,
            @RequestBody UpdateApplicationVenueEnquiryRequest request, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.update(enquiryId, request, authentication));
    }
}
