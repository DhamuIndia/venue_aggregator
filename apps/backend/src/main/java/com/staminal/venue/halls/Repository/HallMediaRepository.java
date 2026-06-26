package com.staminal.venue.halls.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.staminal.venue.halls.Entity.HallMedia;

@Repository
public interface HallMediaRepository extends JpaRepository<HallMedia, Long> {

    List<HallMedia> findByHallId_Id(Long hallId);
}
