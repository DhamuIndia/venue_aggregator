package com.staminal.venue.users;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Catering.VendorCateringDetails;
import com.staminal.venue.vendors.Catering.VendorCateringRepository;
import com.staminal.venue.vendors.Dj.VendorDjDetails;
import com.staminal.venue.vendors.Dj.VendorDjRepository;
import com.staminal.venue.vendors.Hall.VendorHallDetails;
import com.staminal.venue.vendors.Hall.VendorHallRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final VendorHallRepository vendorHallRepository;
    private final VendorDjRepository vendorDjRepository;
    private final VendorCateringRepository vendorCateringRepository;

    public User createUser(User user) {

        user.setFullName(user.getFullName());
        user.setEmail(user.getEmail());
        user.setPhone(user.getPhone());
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
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User getUserByPhone(String phone) {

        return userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public List<VendorHallDetails> getAllHalls() {

        return vendorHallRepository.findByStatus(VendorStatus.APPROVED);
    }

    public List<VendorDjDetails> getAllDj() {
        return vendorDjRepository.findByStatus(VendorStatus.APPROVED);
    }

    public List<VendorCateringDetails> getAllCatering() {
        return vendorCateringRepository.findByStatus(VendorStatus.APPROVED);
    }
}