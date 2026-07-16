package com.staminal.venue.reviews.VendorReview;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

/** Keeps the vendor's public rating fields in sync with published reviews. */
@Service
@RequiredArgsConstructor
public class VendorRatingAggregateService {

    private final VendorReviewRepository vendorReviewRepository;
    private final VendorRepository vendorRepository;

    @Transactional
    public void refreshForVendorId(Long vendorId) {
        Vendors vendor = vendorRepository.findById(vendorId).orElse(null);
        if (vendor == null) {
            return;
        }

        List<VendorReview> reviews = vendorReviewRepository.findByVendor_IdAndActiveTrueAndModerationStatus(
                vendorId,
                ReviewModerationStatus.PUBLISHED);
        double average = reviews.stream()
                .filter(review -> review.getRating() != null)
                .mapToInt(VendorReview::getRating)
                .average()
                .orElse(0.0);

        vendor.setReviewCount(reviews.size());
        vendor.setAverageRating(Math.round(average * 10.0) / 10.0);
        vendorRepository.save(vendor);
    }
}
