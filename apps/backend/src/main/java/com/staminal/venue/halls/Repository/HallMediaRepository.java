package com.staminal.venue.halls.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.halls.Entity.HallMedia;

public interface HallMediaRepository
        extends JpaRepository<HallMedia, Long> {
}