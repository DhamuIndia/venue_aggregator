package com.staminal.venue.halls.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.staminal.venue.halls.Entity.HallBlockedDate;

@Repository
public interface HallBlockedDateRepository extends JpaRepository<HallBlockedDate, Long> {

    List<HallBlockedDate> findByHallId_Id(Long hallId);

    Optional<HallBlockedDate> findByHallId_IdAndEventDateAndSlotType(
            Long hallId,
            LocalDate eventDate,
            String slotType);

    boolean existsByHallId_IdAndEventDateAndSlotType(
            Long hallId,
            LocalDate eventDate,
            String slotType);
}
