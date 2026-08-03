package com.staminal.venue.quotes;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface VendorQuoteRepository extends JpaRepository<VendorQuote, Long> {

    Optional<VendorQuote> findByLead_Id(Long leadId);

    Optional<VendorQuote> findByIdAndLead_Customer_Id(Long quoteId, Long customerId);

    List<VendorQuote> findByVendor_IdOrderByUpdatedAtDesc(Long vendorId);

    List<VendorQuote> findByLead_Customer_IdOrderByUpdatedAtDesc(Long customerId);

    List<VendorQuote> findByLead_Requirement_IdOrderByUpdatedAtDesc(Long requirementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select quote
            from VendorQuote quote
            join fetch quote.lead lead
            left join fetch lead.requirement requirement
            join fetch quote.vendor vendor
            where quote.id = :quoteId
              and lead.customer.id = :customerId
            """)
    Optional<VendorQuote> findOwnedForUpdate(
            @Param("quoteId") Long quoteId,
            @Param("customerId") Long customerId);
}
