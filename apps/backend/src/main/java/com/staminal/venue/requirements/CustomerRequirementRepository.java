package com.staminal.venue.requirements;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CustomerRequirementRepository extends JpaRepository<CustomerRequirement, Long> {

    List<CustomerRequirement> findAllByOrderByCreatedAtDesc();

    List<CustomerRequirement> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    boolean existsByIdAndCustomer_Id(Long requirementId, Long customerId);

    Optional<CustomerRequirement> findByIdAndCustomer_Id(Long requirementId, Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select requirement
            from CustomerRequirement requirement
            where requirement.id = :requirementId
              and requirement.customer.id = :customerId
            """)
    Optional<CustomerRequirement> findOwnedForUpdate(
            @Param("requirementId") Long requirementId,
            @Param("customerId") Long customerId);
}
