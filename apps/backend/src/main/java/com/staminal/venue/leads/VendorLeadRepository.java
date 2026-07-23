package com.staminal.venue.leads;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorLeadRepository
        extends JpaRepository<VendorLead, Long> {

    List<VendorLead> findByVendor_IdOrderByCreatedAtDesc(Long vendorId);

    List<VendorLead> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    Optional<VendorLead> findByIdAndVendor_Id(Long leadId, Long vendorId);

    List<VendorLead> findByRequirement_IdOrderByCreatedAtDesc(Long requirementId);

    boolean existsByRequirement_IdAndVendor_Id(Long requirementId, Long vendorId);
}
