package com.staminal.venue.bookings;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.enums.SlotType;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByEnquiry_Id(Long enquiryId);

    boolean existsByHall_IdAndEventDateAndSlotTypeAndStatus(
            Long hallId,
            LocalDate eventDate,
            SlotType slotType,
            String status);
}
