package com.staminal.venue.users.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.enums.UserRole;
import com.staminal.venue.users.Entity.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(UserRole name);
}
