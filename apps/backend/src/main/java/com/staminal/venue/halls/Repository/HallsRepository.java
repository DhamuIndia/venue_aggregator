package com.staminal.venue.halls.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.halls.Entity.Halls;

public interface HallsRepository extends JpaRepository<Halls, Long> {

}
