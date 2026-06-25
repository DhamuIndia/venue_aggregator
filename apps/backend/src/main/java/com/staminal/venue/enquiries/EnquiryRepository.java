package com.staminal.venue.enquiries;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {

    List<Enquiry> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    List<Enquiry> findByHall_IdOrderByCreatedAtDesc(Long hallId);

}
