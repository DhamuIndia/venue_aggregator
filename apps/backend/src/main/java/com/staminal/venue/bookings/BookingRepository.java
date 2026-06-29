package com.staminal.venue.bookings;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.enums.BookingStatus;
import com.staminal.venue.enums.SlotType;

public interface BookingRepository extends JpaRepository<Booking, Long> {

        Optional<Booking> findByEnquiry_Id(Long enquiryId);

        List<Booking> findByCustomer_IdOrderByEventDateDesc(Long customerId);

        List<Booking> findByHall_IdOrderByEventDateDesc(Long hallId);

        boolean existsByHall_IdAndEventDateAndSlotTypeAndStatus(
                        Long hallId,
                        LocalDate eventDate,
                        SlotType slotType,
                        BookingStatus status);

        boolean existsByHall_IdAndEventDateAndSlotTypeAndStatusAndIdNot(
                        Long hallId,
                        LocalDate eventDate,
                        SlotType slotType,
                        BookingStatus status,
                        Long id);

        List<Booking> findByHall_IdAndStatus(
                        Long hallId,
                        BookingStatus status);
}
