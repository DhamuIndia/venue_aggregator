package com.staminal.venue.reviews.HallReview;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

        Optional<Review> findByEnquiry_Id(Long enquiryId);

        List<Review> findByCustomer_Id(Long customerId);

        boolean existsByEnquiry_IdAndActiveTrue(Long enquiryId);

        boolean existsByEnquiry_Id(Long enquiryId);

        List<Review> findByHall_IdAndActiveTrue(Long hallId);

        @Query("""
                        SELECT r
                        FROM Review r
                        LEFT JOIN FETCH r.hall
                        LEFT JOIN FETCH r.vendor
                        LEFT JOIN FETCH r.customer
                        ORDER BY r.createdAt DESC
                        """)
        List<Review> findAllForAdmin();

        @Query("""
                        SELECT r
                        FROM Review r
                        LEFT JOIN FETCH r.hall
                        LEFT JOIN FETCH r.vendor
                        LEFT JOIN FETCH r.customer
                        WHERE r.moderationStatus IN :statuses
                        ORDER BY r.createdAt DESC
                        """)
        List<Review> findForAdminByStatuses(@Param("statuses") Collection<ReviewModerationStatus> statuses);

        @Query("""
                        SELECT r
                        FROM Review r
                        LEFT JOIN FETCH r.hall
                        LEFT JOIN FETCH r.vendor
                        LEFT JOIN FETCH r.customer
                        WHERE r.id = :reviewId
                        """)
        Optional<Review> findByIdForAdmin(@Param("reviewId") Long reviewId);

        @Query("""
                                    SELECT r
                                    FROM Review r
                                    LEFT JOIN FETCH r.customer
                                    WHERE r.hall.id = :hallId
                                    AND r.active = true
                        AND r.moderationStatus = com.staminal.venue.reviews.HallReview.ReviewModerationStatus.PUBLISHED            """)
        List<Review> findPublicReviewsByHallId(@Param("hallId") Long hallId);

        @Query("""
                        SELECT r
                        FROM Review r
                        LEFT JOIN FETCH r.customer
                        LEFT JOIN FETCH r.enquiry
                        LEFT JOIN FETCH r.booking
                        WHERE r.hall.id = :hallId
                        AND r.active = true
                        AND r.moderationStatus = com.staminal.venue.reviews.HallReview.ReviewModerationStatus.PUBLISHED
                        ORDER BY r.createdAt DESC
                        """)
        List<Review> findOwnerPublishedReviewsByHallId(@Param("hallId") Long hallId);

        @Query("""
                        SELECT r
                        FROM Review r
                        LEFT JOIN FETCH r.customer
                        LEFT JOIN FETCH r.enquiry
                        LEFT JOIN FETCH r.booking
                        WHERE r.vendor.id = :vendorId
                        AND r.active = true
                        AND r.moderationStatus = com.staminal.venue.reviews.HallReview.ReviewModerationStatus.PUBLISHED
                        ORDER BY r.createdAt DESC
                        """)
        List<Review> findPublishedReviewsByVendorId(@Param("vendorId") Long vendorId);

        boolean existsByCustomer_IdAndHall_Id(Long customerId, Long hallId);

        List<Review> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

        Optional<Review> findByIdAndCustomer_Id(Long reviewId, Long customerId);

}
