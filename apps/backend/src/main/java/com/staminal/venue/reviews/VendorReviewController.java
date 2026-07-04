package com.staminal.venue.reviews;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.vendors.Dto.VendorReviewListResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/vendor")
@RequiredArgsConstructor
public class VendorReviewController {

    private final VendorReviewService vendorReviewService;

    @GetMapping("/reviews")
    public VendorReviewListResponse getMyReviews(Authentication authentication) {
        return vendorReviewService.getMyReviews(authentication);
    }
}
