package com.staminal.venue.halls.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.users.Entity.User;

public interface HallRepository extends JpaRepository<Halls, Long> {

    List<Halls> findByStatus(HallStatus status);

    List<Halls> findByOwnerUserId(User ownerUserId);

    List<Halls> findByOwnerUserId_Id(Long ownerUserId);

}
