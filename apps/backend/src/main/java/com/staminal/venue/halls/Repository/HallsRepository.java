package com.staminal.venue.halls.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.halls.Entity.Halls;

public interface HallsRepository extends JpaRepository<Halls, Long> {

    List<Halls> findByStatus(HallStatus status);

}
