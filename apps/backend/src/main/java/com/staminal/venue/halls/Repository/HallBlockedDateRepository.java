package com.staminal.venue.halls.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.halls.Entity.HallBlockedDate;

public interface HallBlockedDateRepository extends JpaRepository<HallBlockedDate, Long> {

}
