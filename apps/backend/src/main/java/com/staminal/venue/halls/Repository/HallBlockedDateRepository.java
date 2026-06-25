package com.staminal.venue.halls.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.staminal.venue.halls.Entity.HallBlockedDate;

@Repository
public interface HallBlockedDateRepository extends JpaRepository<HallBlockedDate, Long> {

    List<HallBlockedDate> findByHallId_Id(Long hallId);
}
