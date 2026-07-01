package com.staminal.venue.reviews;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByEnquiry_Id(Long enquiryId);

    List<Review> findByCustomer_Id(Long customerId);

    boolean existsByEnquiry_IdAndActiveTrue(Long enquiryId);

    List<Review> findByHall_IdAndActiveTrue(Long hallId);

    @Query("""
            SELECT r
            FROM Review r
            LEFT JOIN FETCH r.customer
            WHERE r.hall.id = :hallId
            AND r.active = true
            """)
    List<Review> findPublicReviewsByHallId(@Param("hallId") Long hallId);

}