package com.staminal.venue.reviews;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByEnquiry_IdAndActiveTrue(Long enquiryId);

    List<Review> findByCustomer_Id(Long customerId);


    boolean existsByEnquiry_IdAndActiveTrue(Long enquiryId);
}