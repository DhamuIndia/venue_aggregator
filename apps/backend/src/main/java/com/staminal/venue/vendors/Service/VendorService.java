package com.staminal.venue.vendors.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.auth.service.JwtService;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Dto.CreateVendorRequest;
import com.staminal.venue.vendors.Dto.UpdateVendorRequest;
import com.staminal.venue.vendors.Dto.VendorLoginRequest;
import com.staminal.venue.vendors.Dto.VendorLoginResponse;
import com.staminal.venue.vendors.Dto.VendorResponse;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorCategoryRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorService {

        private final VendorRepository vendorRepository;
        private final VendorCategoryRepository vendorCategoryRepository;
        private final JwtService jwtService;
        private final UserRepository userRepository;

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

                vendor.setYearsInBusiness(request.getYearsInBusiness());

                vendor.setServiceRadius(request.getServiceRadius());

                vendor.setServices(request.getServices());

                vendor.setPackageName(request.getPackageName());

                vendor.setStartingPrice(request.getStartingPrice());

                vendor.setPackageDescription(request.getPackageDescription());

                Set<VendorCategory> categories = request.getCategoryIds() == null
                                ? new HashSet<>()
                                : new HashSet<>(vendorCategoryRepository.findAllById(request.getCategoryIds()));

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
                response.setYearsInBusiness(vendor.getYearsInBusiness());

                response.setServiceRadius(vendor.getServiceRadius());

                response.setServices(vendor.getServices());

                response.setPackageName(vendor.getPackageName());

                response.setStartingPrice(vendor.getStartingPrice());

                response.setPackageDescription(vendor.getPackageDescription());
                response.setUpdatedAt(vendor.getUpdatedAt());

                VendorStatus status = vendor.getStatus() == null ? VendorStatus.DRAFT : vendor.getStatus();
                if (status == VendorStatus.PENDING) {
                        response.setStatus("PENDING_APPROVAL");
                } else {
                        response.setStatus(status.name());
                }

                Set<VendorCategory> categories = vendor.getCategories() == null ? Set.of() : vendor.getCategories();
                response.setCategories(categories.stream()
                                .map(VendorCategory::getCategoryName)
                                .collect(Collectors.toSet()));
                response.setCategory(categories.stream()
                                .findFirst()
                                .map(VendorCategory::getCategoryName)
                                .map(this::toFrontendCategory)
                                .orElse("CATERING"));

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

                String token = vendor.getUser() == null
                                ? jwtService.generateToken(vendor.getEmail(), "VENDOR")
                                : jwtService.generateAccessToken(vendor.getUser().getId(), "VENDOR");

                VendorLoginResponse response = new VendorLoginResponse();

                response.setMessage(
                                "Login Successful");

                response.setToken(token);

                return response;
        }

        @Transactional(readOnly = true)
        public VendorResponse getProfile(String userId) {
                Long currentUserId = parseUserId(userId);
                return vendorRepository.findByUserId(currentUserId)
                                .map(this::mapToResponse)
                                .orElseGet(() -> mapDraftProfile(findUser(currentUserId)));
        }

        @Transactional
        public VendorResponse updateProfile(
                        String userId,
                        UpdateVendorRequest request) {

                User user = findUser(parseUserId(userId));
                Vendors vendor = vendorRepository.findByUserId(user.getId())
                                .orElseGet(() -> newDraftVendor(user));

                vendor.setBusinessName(defaultText(request.getBusinessName(), user.getFullName()));
                vendor.setCity(defaultText(request.getCity(), "Chennai"));
                vendor.setArea(defaultText(request.getArea(), ""));
                vendor.setDescription(trimToNull(request.getDescription()));
                vendor.setYearsInBusiness(request.getYearsInBusiness());
                vendor.setServiceRadius(request.getServiceRadius());
                vendor.setServices(request.getServices() == null ? new ArrayList<>() : new ArrayList<>(request.getServices()));
                vendor.setPackageName(trimToNull(request.getPackageName()));
                vendor.setStartingPrice(request.getStartingPrice());
                vendor.setPackageDescription(trimToNull(request.getPackageDescription()));
                vendor.setCategories(categoriesFor(request.getCategory()));
                if (vendor.getStatus() == null
                                || vendor.getStatus() == VendorStatus.PENDING
                                || vendor.getStatus() == VendorStatus.REJECTED) {
                        vendor.setStatus(VendorStatus.DRAFT);
                }

                vendor.setUpdatedAt(Instant.now());

                Vendors saved = vendorRepository.save(vendor);

                return mapToResponse(saved);
        }

        @Transactional
        public VendorResponse submitProfile(String userId) {
                Vendors vendor = vendorRepository
                                .findByUserId(parseUserId(userId))
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Save the vendor profile before submitting"));

                validateSubmittable(vendor);

                vendor.setStatus(VendorStatus.PENDING);

                vendor.setUpdatedAt(Instant.now());

                Vendors saved = vendorRepository.save(vendor);

                return mapToResponse(saved);
        }

        private User findUser(Long userId) {
                return userRepository.findById(userId)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        }

        private Long parseUserId(String userId) {
                try {
                        return Long.parseLong(userId);
                } catch (NumberFormatException exception) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
                }
        }

        private Vendors newDraftVendor(User user) {
                Vendors vendor = new Vendors();
                vendor.setUser(user);
                vendor.setVendorName(defaultText(user.getFullName(), "Vendor"));
                vendor.setBusinessName(defaultText(user.getFullName(), "Vendor business"));
                vendor.setCoverImageUrl("");
                vendor.setAddressLine("");
                vendor.setCity("Chennai");
                vendor.setArea("");
                vendor.setEmail(user.getEmail());
                vendor.setContactNumber(user.getPhone());
                vendor.setWhatsAppNumber(user.getPhone());
                vendor.setPasswordHash(user.getPasswordHash());
                vendor.setStatus(VendorStatus.DRAFT);
                vendor.setServices(new ArrayList<>());
                vendor.setCategories(new HashSet<>());
                vendor.setCreatedAt(Instant.now());
                vendor.setUpdatedAt(Instant.now());
                return vendor;
        }

        private VendorResponse mapDraftProfile(User user) {
                VendorResponse response = new VendorResponse();
                response.setVendorName(defaultText(user.getFullName(), "Vendor"));
                response.setBusinessName(defaultText(user.getFullName(), "Vendor business"));
                response.setCategory("CATERING");
                response.setCity("Chennai");
                response.setArea("");
                response.setContactNumber(user.getPhone());
                response.setWhatsAppNumber(user.getPhone());
                response.setStatus(VendorStatus.DRAFT.name());
                response.setServices(List.of());
                response.setCategories(Set.of());
                response.setUpdatedAt(Instant.now());
                return response;
        }

        private Set<VendorCategory> categoriesFor(String category) {
                String databaseName = toDatabaseCategory(category);
                if (databaseName == null) {
                        return new HashSet<>();
                }

                return vendorCategoryRepository.findAll().stream()
                                .filter(candidate -> candidate.getCategoryName() != null
                                                && candidate.getCategoryName().equalsIgnoreCase(databaseName))
                                .findFirst()
                                .map(candidate -> new HashSet<>(Set.of(candidate)))
                                .orElseGet(HashSet::new);
        }

        private String toDatabaseCategory(String category) {
                if (category == null || category.isBlank()) {
                        return null;
                }
                String normalized = category.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
                return switch (normalized) {
                        case "CATERING" -> "Catering";
                        case "DECORATION", "DECOR" -> "Decoration";
                        case "PHOTOGRAPHY" -> "Photography";
                        case "BRIDAL_MAKEUP", "MAKEUP" -> "Makeup";
                        case "MUSIC_AND_DJ", "MUSIC_DJ", "DJ" -> "DJ";
                        case "EVENT_PLANNING", "PLANNING" -> "Wedding Planner";
                        default -> null;
                };
        }

        private String toFrontendCategory(String categoryName) {
                if (categoryName == null || categoryName.isBlank()) {
                        return "CATERING";
                }
                String normalized = categoryName.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
                return switch (normalized) {
                        case "CATERING", "FOOD" -> "CATERING";
                        case "DECORATION", "BALLOON_DECORATION" -> "DECORATION";
                        case "PHOTOGRAPHY" -> "PHOTOGRAPHY";
                        case "MAKEUP", "MEHENDI" -> "BRIDAL_MAKEUP";
                        case "DJ", "LIVE_MUSIC" -> "MUSIC_AND_DJ";
                        case "WEDDING_PLANNER" -> "EVENT_PLANNING";
                        default -> "CATERING";
                };
        }

        private void validateSubmittable(Vendors vendor) {
                if (isBlank(vendor.getBusinessName())
                                || isBlank(vendor.getCity())
                                || isBlank(vendor.getArea())
                                || isBlank(vendor.getPackageName())
                                || vendor.getStartingPrice() == null
                                || vendor.getStartingPrice().compareTo(BigDecimal.ZERO) <= 0
                                || vendor.getServices() == null
                                || vendor.getServices().isEmpty()) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Complete business, location, services, package, and starting price before submitting");
                }
        }

        private String defaultText(String value, String fallback) {
                String trimmed = trimToNull(value);
                return trimmed == null ? fallback : trimmed;
        }

        private String trimToNull(String value) {
                if (value == null) {
                        return null;
                }
                String trimmed = value.trim();
                return trimmed.isEmpty() ? null : trimmed;
        }

        private boolean isBlank(String value) {
                return value == null || value.isBlank();
        }
}
