package com.staminal.venue.halls;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.halls.Entity.Halls;

public interface HallRepository extends JpaRepository<Halls, Long> {

    List<Halls> findByStatus(String status);

    List<Halls> findByOwnerUserId(Long ownerUserId);

}
