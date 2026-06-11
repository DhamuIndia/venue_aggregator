package com.staminal.venue.users.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staminal.venue.users.Entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}