package com.staminal.venue.enquiries;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.staminal.venue.enums.EnquiryStatus;
import jakarta.persistence.LockModeType;

public interface EnquiryRepository extends JpaRepository<Enquiry, Long> {

    List<Enquiry> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    List<Enquiry> findByHall_IdOrderByCreatedAtDesc(Long hallId);

    Optional<Enquiry> findByIdAndCustomer_Id(Long enquiryId, Long customerId);

    Page<Enquiry> findByRoutingTarget(EnquiryRoutingTarget routingTarget, Pageable pageable);

    Page<Enquiry> findByRoutingTargetAndStatus(EnquiryRoutingTarget routingTarget, EnquiryStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Enquiry e where e.id = :id and e.routingTarget = :routingTarget")
    Optional<Enquiry> findForTeamUpdate(@Param("id") Long id, @Param("routingTarget") EnquiryRoutingTarget routingTarget);

}
