package com.staminal.venue.customer.savedhalls;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerSavedHallRepository extends JpaRepository<CustomerSavedHall, Long> {

    List<CustomerSavedHall> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    Optional<CustomerSavedHall> findByCustomer_IdAndHall_Id(Long customerId, Long hallId);
}
