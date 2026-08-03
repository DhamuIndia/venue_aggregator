package com.staminal.venue.reviews.VendorReview;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import com.staminal.venue.reviews.HallReview.ReviewModerationStatus;

public interface VendorReviewRepository
                extends JpaRepository<VendorReview, Long> {

        boolean existsByVendorLead_Id(Long leadId);

        List<VendorReview> findByVendor_Id(Long vendorId);

        List<VendorReview> findByCustomer_Id(Long customerId);

        List<VendorReview> findByVendor_IdAndActiveTrue(Long vendorId);

        List<VendorReview> findByVendor_IdAndActiveTrueAndModerationStatus(
                        Long vendorId,
                        ReviewModerationStatus moderationStatus);

        List<VendorReview> findByModerationStatusIn(
                        Set<ReviewModerationStatus> statuses);

        List<VendorReview> findByModerationStatus(
                        ReviewModerationStatus status);

        boolean existsByCustomer_IdAndVendor_Id(
                        Long customerId,
                        Long vendorId);

}
