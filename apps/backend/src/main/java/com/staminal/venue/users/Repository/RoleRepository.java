package com.staminal.venue.users.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.users.Entity.Role;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);
}