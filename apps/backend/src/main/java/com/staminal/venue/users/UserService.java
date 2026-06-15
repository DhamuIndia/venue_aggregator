package com.staminal.venue.users;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User createUser(User user) {

        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user.setStatus("ACTIVE");

        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {

        return userRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
    }

    public User getUserByPhone(String phone) {

        return userRepository.findByPhone(phone)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));
    }
}