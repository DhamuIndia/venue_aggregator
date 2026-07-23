package com.staminal.venue.vendorbookings;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.staminal.venue.enums.VendorServiceBookingStatus;

import jakarta.persistence.LockModeType;

public interface VendorServiceBookingRepository extends JpaRepository<VendorServiceBooking, Long> {

    boolean existsByQuote_Id(Long quoteId);

    Optional<VendorServiceBooking> findByLead_Id(Long leadId);

    List<VendorServiceBooking> findByCustomer_IdOrderByEventDateDesc(Long customerId);

    List<VendorServiceBooking> findByVendor_IdOrderByEventDateDesc(Long vendorId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from VendorServiceBooking b where b.id = :id")
    Optional<VendorServiceBooking> findForUpdate(@Param("id") Long id);

    List<VendorServiceBooking> findByStatusAndEventDate(VendorServiceBookingStatus status, java.time.LocalDate eventDate);
}
