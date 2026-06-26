package com.staminal.venue.vendors.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staminal.venue.auth.service.JwtService;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.users.Entity.Role;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.RoleRepository;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Dto.CreateVendorRequest;
import com.staminal.venue.vendors.Dto.VendorLoginRequest;
import com.staminal.venue.vendors.Dto.VendorLoginResponse;
import com.staminal.venue.vendors.Dto.VendorResponse;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorCategoryRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;
import com.staminal.venue.vendors.Dto.UpdateVendorRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorService {

        private final VendorRepository vendorRepository;
        private final VendorCategoryRepository vendorCategoryRepository;
        private final JwtService jwtService;
        private final UserRepository userRepository;
        private final RoleRepository roleRepository;
        private final PasswordEncoder passwordEncoder;

        public VendorResponse createVendor(String userId, CreateVendorRequest request) {
                // User user = new User();
                // user.setFullName(request.getVendorName());
                // user.setPhone(request.getContactNumber());
                // user.setEmail(request.getEmail());
                // user.setPasswordHash(passwordEncoder.encode(request.getPasswordHash()));
                // user.setStatus("ACTIVE");

                // Role vendorRole = roleRepository.findByName(UserRole.VENDOR)
                // .orElseThrow(() -> new RuntimeException("Vendor role not found"));

                // user.getRoles().add(vendorRole);

                // User savedUser = userRepository.save(user);

                User savedUser = userRepository.findById(Long.parseLong(userId))
                                .orElseThrow(() -> new RuntimeException("User not found"));

                Vendors vendor = new Vendors();
                vendor.setVendorName(savedUser.getFullName());
                vendor.setBusinessName(request.getBusinessName());
                vendor.setDescription(request.getDescription());
                vendor.setCoverImageUrl(request.getCoverImageUrl());
                vendor.setAddressLine(request.getAddressLine());
                vendor.setEmail(savedUser.getEmail());
                vendor.setCity(request.getCity());
                vendor.setArea(request.getArea());
                vendor.setPincode(request.getPincode());
                vendor.setLatitude(request.getLatitude());
                vendor.setLongitude(request.getLongitude());
                vendor.setContactNumber(request.getContactNumber());
                vendor.setWhatsAppNumber(request.getWhatsAppNumber());
                vendor.setStatus(VendorStatus.PENDING);
                vendor.setPasswordHash(savedUser.getPasswordHash());
                vendor.setCreatedAt(Instant.now());
                vendor.setUpdatedAt(Instant.now());
                vendor.setUser(savedUser);

                Set<VendorCategory> categories = new HashSet<>(
                                vendorCategoryRepository.findAllById(
                                                request.getCategoryIds()));

                vendor.setCategories(categories);

                Vendors savedVendor = vendorRepository.save(vendor);

                return mapToResponse(savedVendor);
        }

        @Transactional(readOnly = true)
        public List<VendorResponse> getAllVendors() {

                return vendorRepository.findAll()
                                .stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        private VendorResponse mapToResponse(Vendors vendor) {

                VendorResponse response = new VendorResponse();

                response.setId(vendor.getId());
                response.setVendorName(vendor.getVendorName());
                response.setBusinessName(vendor.getBusinessName());
                response.setDescription(vendor.getDescription());
                response.setCoverImageUrl(vendor.getCoverImageUrl());
                response.setCity(vendor.getCity());
                response.setArea(vendor.getArea());
                response.setContactNumber(vendor.getContactNumber());
                response.setWhatsAppNumber(vendor.getWhatsAppNumber());
                if (vendor.getStatus() == VendorStatus.PENDING) {
                        response.setStatus("PENDING_APPROVAL");
                } else {
                        response.setStatus(vendor.getStatus().name());
                }

                response.setCategories(
                                vendor.getCategories()
                                                .stream()
                                                .map(VendorCategory::getCategoryName)
                                                .collect(Collectors.toSet()));

                return response;
        }

        @Transactional(readOnly = true)
        public VendorResponse getVendorById(Long id) {

                Vendors vendor = vendorRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Vendor not found"));

                return mapToResponse(vendor);
        }

        public List<VendorResponse> getVendorsByCategory(
                        String categoryName) {

                List<Vendors> vendors = vendorRepository.findByCategories_CategoryName(
                                categoryName);

                return vendors.stream()
                                .map(this::mapToResponse)
                                .toList();
        }

        public VendorLoginResponse login(
                        VendorLoginRequest request) {

                Vendors vendor = vendorRepository
                                .findByEmail(request.getEmail())
                                .orElseThrow(() -> new RuntimeException("Vendor not found"));

                if (!vendor.getPasswordHash()
                                .equals(request.getPasswordHash())) {

                        throw new RuntimeException(
                                        "Invalid password");
                }

                if (vendor.getStatus() != VendorStatus.APPROVED) {

                        throw new RuntimeException(
                                        "Vendor not approved");
                }

                String token = jwtService.generateToken(
                                vendor.getEmail(),
                                "VENDOR");

                VendorLoginResponse response = new VendorLoginResponse();

                response.setMessage(
                                "Login Successful");

                response.setToken(token);

                return response;
        }

        @Transactional(readOnly = true)
        public VendorResponse getProfile(String userId) {

                Vendors vendor = vendorRepository
                                .findByUserId(Long.parseLong(userId))
                                .orElseThrow(() -> new RuntimeException("Vendor not found"));

                return mapToResponse(vendor);
        }

        @Transactional
        public VendorResponse updateProfile(
                        String userId,
                        UpdateVendorRequest request) {

                Vendors vendor = vendorRepository.findByUserId(Long.parseLong(userId))
                                .orElseThrow(() -> new RuntimeException("Vendor not found"));

                vendor.setBusinessName(request.getBusinessName());
                vendor.setCity(request.getCity());
                vendor.setArea(request.getArea());
                vendor.setDescription(request.getDescription());

                vendor.setUpdatedAt(Instant.now());

                Vendors saved = vendorRepository.save(vendor);

                return mapToResponse(saved);
        }

        @Transactional
        public VendorResponse submitProfile(String userId) {
                Vendors vendor = vendorRepository
                                .findByUserId(Long.parseLong(userId))
                                .orElseThrow(() -> new RuntimeException("Vendor not found"));

                vendor.setStatus(VendorStatus.PENDING);

                vendor.setUpdatedAt(Instant.now());

                Vendors saved = vendorRepository.save(vendor);

                return mapToResponse(saved);
        }
}
