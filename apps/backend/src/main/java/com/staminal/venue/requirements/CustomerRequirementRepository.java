package com.staminal.venue.requirements;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRequirementRepository extends JpaRepository<CustomerRequirement, Long> {

    List<CustomerRequirement> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    boolean existsByIdAndCustomer_Id(Long requirementId, Long customerId);
}
