package com.staminal.venue.dev;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.staminal.venue.admin.Admin;
import com.staminal.venue.admin.AdminRepository;
import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.enums.VendorServiceType;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.halls.Entity.HallMedia;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallMediaRepository;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.users.Entity.Role;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.RoleRepository;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.VendorMedia;
import com.staminal.venue.vendors.Entity.VendorPackage;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorCategoryRepository;
import com.staminal.venue.vendors.Repository.VendorMediaRepository;
import com.staminal.venue.vendors.Repository.VendorPackageRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "dev-data", havingValue = "true")
public class DevSeedDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedDataRunner.class);
    private static final String DEFAULT_PASSWORD = "Password123";
    private static final String ACTIVE = "ACTIVE";
    private static final String EMERALD_IMAGE_URL =
            "https://images.unsplash.com/photo-1519167758481-83f550bb49b3?auto=format&fit=crop&w=1200&q=85";
    private static final String MARINA_IMAGE_URL =
            "https://images.unsplash.com/photo-1464366400600-7168b8af9bc3?auto=format&fit=crop&w=1200&q=85";
    private static final String VENDOR_IMAGE_URL =
            "https://images.unsplash.com/photo-1555244162-803834f70033?auto=format&fit=crop&w=1200&q=82";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AdminRepository adminRepository;
    private final HallRepository hallRepository;
    private final HallMediaRepository hallMediaRepository;
    private final VendorRepository vendorRepository;
    private final VendorCategoryRepository vendorCategoryRepository;
    private final VendorPackageRepository vendorPackageRepository;
    private final VendorMediaRepository vendorMediaRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User customer = ensureUser("Test Customer", "9876543210", "customer@example.com", UserRole.CUSTOMER);
        User owner = ensureUser("Arun Kumar", "9876501234", "owner@example.com", UserRole.HALL_OWNER);
        User vendorUser = ensureUser("Meera Vendor", "9884012345", "vendor@example.com", UserRole.VENDOR);
        User adminUser = ensureUser("Test Admin", "9000000001", "admin@example.com", UserRole.ADMIN);

        Admin admin = ensureAdmin("Test Admin", "admin@example.com", "9000000001");

        Halls approvedHall = ensureHall(
                "Emerald Convention Centre",
                owner,
                HallStatus.APPROVED,
                admin,
                "Spacious AC convention centre for weddings, receptions, and corporate events.",
                EMERALD_IMAGE_URL,
                "OMR",
                "No. 12, Rajiv Gandhi Salai",
                250,
                900,
                new BigDecimal("65000.00"),
                new BigDecimal("75000.00"),
                new BigDecimal("125000.00"),
                4.7);
        ensureHallMedia(approvedHall, EMERALD_IMAGE_URL, true, 0);
        ensureHallMedia(approvedHall, MARINA_IMAGE_URL, false, 1);

        ensureHall(
                "Marina Grand Palace",
                owner,
                HallStatus.PENDING_APPROVAL,
                null,
                "Premium banquet hall near the city centre, waiting for admin review.",
                MARINA_IMAGE_URL,
                "Mylapore",
                "No. 44, Luz Church Road",
                120,
                450,
                new BigDecimal("40000.00"),
                new BigDecimal("50000.00"),
                new BigDecimal("85000.00"),
                0.0);

        VendorCategory catering = ensureVendorCategory("Catering");
        Vendors vendor = ensureVendor(vendorUser, adminUser, catering);
        ensureVendorPackage(vendor);
        ensureVendorMedia(vendor, VENDOR_IMAGE_URL);

        log.info(
                "Local dev seed data ready. Password for all seeded users is {}. Customer {}, owner {}, vendor {}, admin {}.",
                DEFAULT_PASSWORD,
                customer.getPhone(),
                owner.getPhone(),
                vendorUser.getPhone(),
                adminUser.getPhone());
    }

    private User ensureUser(String fullName, String phone, String email, UserRole roleName) {
        Role role = ensureRole(roleName);
        User user = userRepository.findByPhone(phone)
                .or(() -> userRepository.findByEmail(email))
                .orElseGet(User::new);

        user.setFullName(fullName);
        user.setPhone(phone);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setStatus(ACTIVE);
        user.getRoles().add(role);

        return userRepository.save(user);
    }

    private Role ensureRole(UserRole roleName) {
        return roleRepository.findByName(roleName)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(roleName);
                    return roleRepository.save(role);
                });
    }

    private Admin ensureAdmin(String fullName, String email, String contactNumber) {
        Instant now = Instant.now();
        Admin admin = adminRepository.findByEmail(email).orElseGet(Admin::new);
        admin.setFullName(fullName);
        admin.setEmail(email);
        admin.setContactNumber(contactNumber);
        admin.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        admin.setStatus(ACTIVE);
        if (admin.getCreatedAt() == null) {
            admin.setCreatedAt(now);
        }
        admin.setUpdatedAt(now);
        return adminRepository.save(admin);
    }

    private Halls ensureHall(
            String name,
            User owner,
            HallStatus status,
            Admin admin,
            String description,
            String coverImageUrl,
            String area,
            String addressLine,
            int capacityMin,
            int capacityMax,
            BigDecimal morningAmount,
            BigDecimal eveningAmount,
            BigDecimal fullDayAmount,
            double ratings) {
        LocalDateTime now = LocalDateTime.now();
        Halls hall = findHallByName(name);
        hall.setOwnerUserId(owner);
        hall.setOwnerName(owner.getFullName());
        hall.setName(name);
        hall.setDescription(description);
        hall.setCoverImageUrl(coverImageUrl);
        hall.setAddressLine(addressLine);
        hall.setCity("Chennai");
        hall.setArea(area);
        hall.setPincode("600004");
        hall.setLatitude(13.0827);
        hall.setLongitude(80.2707);
        hall.setCapacityMin(capacityMin);
        hall.setCapacityMax(capacityMax);
        hall.setFloors(2);
        hall.setAcAvailable(true);
        hall.setHallType("BANQUET");
        hall.setRatings(ratings);
        hall.setRooms(6);
        hall.setCarParking(true);
        hall.setBikeParking(true);
        hall.setDiningAvailable(true);
        hall.setDiningCapacity(Math.min(capacityMax, 350));
        hall.setGeneratorAvailable(true);
        hall.setLiftAvailable(true);
        hall.setContactNumber("9876501234");
        hall.setWhatsappNumber("9876501234");
        hall.setStatus(status);
        hall.setRejectionReason(null);
        hall.setBridalRoomAvailable(true);
        hall.setCateringKitchenAvailable(true);
        hall.setMorningAmount(morningAmount);
        hall.setEveningAmount(eveningAmount);
        hall.setFullDayAmount(fullDayAmount);
        hall.setApprovedBy(status == HallStatus.APPROVED ? admin : null);
        hall.setApprovedAt(status == HallStatus.APPROVED ? now.minusDays(2) : null);
        if (hall.getCreatedAt() == null) {
            hall.setCreatedAt(now.minusDays(10));
        }
        hall.setUpdatedAt(now);
        return hallRepository.save(hall);
    }

    private Halls findHallByName(String name) {
        return hallRepository.findAll()
                .stream()
                .filter(hall -> equalsIgnoreCase(hall.getName(), name))
                .findFirst()
                .orElseGet(Halls::new);
    }

    private void ensureHallMedia(Halls hall, String url, boolean primary, int sortOrder) {
        String publicId = "dev/" + slugify(hall.getName()) + "/" + sortOrder;
        List<HallMedia> matchingMedia = hallMediaRepository.findByHallId_Id(hall.getId())
                .stream()
                .filter(media -> publicId.equals(media.getPublicId()) || url.equals(media.getUrl()))
                .toList();
        HallMedia media = matchingMedia.isEmpty() ? new HallMedia() : matchingMedia.getFirst();

        media.setHallId(hall);
        media.setMediaType("IMAGE");
        media.setUrl(url);
        media.setPublicId(publicId);
        media.setIsPrimary(primary);
        media.setSortOrder(sortOrder);
        if (media.getCreatedAt() == null) {
            media.setCreatedAt(LocalDateTime.now());
        }
        hallMediaRepository.save(media);
        matchingMedia.stream().skip(1).forEach(hallMediaRepository::delete);
    }

    private VendorCategory ensureVendorCategory(String categoryName) {
        return vendorCategoryRepository.findAll()
                .stream()
                .filter(category -> equalsIgnoreCase(category.getCategoryName(), categoryName))
                .findFirst()
                .orElseGet(() -> {
                    VendorCategory category = new VendorCategory();
                    category.setCategoryName(categoryName);
                    category.setCreatedAt(Instant.now());
                    return vendorCategoryRepository.save(category);
                });
    }

    private Vendors ensureVendor(User vendorUser, User adminUser, VendorCategory category) {
        Instant now = Instant.now();
        Vendors vendor = vendorRepository.findByUserId(vendorUser.getId())
                .or(() -> vendorRepository.findByEmail("vendor@example.com"))
                .orElseGet(Vendors::new);
        vendor.setUser(vendorUser);
        vendor.setVendorName(vendorUser.getFullName());
        vendor.setBusinessName("Saffron Leaf Catering");
        vendor.setCoverImageUrl(VENDOR_IMAGE_URL);
        vendor.setDescription("Traditional and premium event catering for weddings, receptions, and corporate events.");
        vendor.setAddressLine("No. 18, TTK Road");
        vendor.setCity("Chennai");
        vendor.setArea("Alwarpet");
        vendor.setPincode("600018");
        vendor.setLatitude(13.0338);
        vendor.setLongitude(80.2544);
        vendor.setEmail("vendor@example.com");
        vendor.setContactNumber("9884012345");
        vendor.setWhatsAppNumber("9884012345");
        vendor.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        vendor.setStatus(VendorStatus.APPROVED);
        vendor.setRejectionReason(null);
        vendor.setReviewedByUser(adminUser);
        vendor.setReviewedAt(now.minusSeconds(172800));
        vendor.setYearsInBusiness(8);
        vendor.setServiceRadius(35);
        vendor.setPackageName("Classic Wedding Feast");
        vendor.setStartingPrice(new BigDecimal("450.00"));
        vendor.setPackageDescription("South Indian wedding menu with service team and live counters.");
        vendor.setServices(new ArrayList<>(
                List.of("Veg meals", "Live dosa counter", "Welcome drinks", "Dessert station")));
        vendor.setCategories(new HashSet<>(Set.of(category)));
        if (vendor.getCreatedAt() == null) {
            vendor.setCreatedAt(now.minusSeconds(604800));
        }
        vendor.setUpdatedAt(now);
        return vendorRepository.save(vendor);
    }

    private void ensureVendorPackage(Vendors vendor) {
        boolean exists = vendorPackageRepository.findByVendor_Id(vendor.getId())
                .stream()
                .anyMatch(row -> equalsIgnoreCase(row.getPackageName(), "Classic Wedding Feast"));
        if (exists) {
            return;
        }

        VendorPackage packageRow = new VendorPackage();
        packageRow.setVendor(vendor);
        packageRow.setPackageName("Classic Wedding Feast");
        packageRow.setDescription("Complete wedding catering package for 300+ guests.");
        packageRow.setPrice(new BigDecimal("450.00"));
        packageRow.setServiceType(VendorServiceType.CATERING);
        packageRow.setServiceId(vendor.getId());
        packageRow.setIncludes(new ArrayList<>(List.of(
                "Banana leaf service",
                "Two sweet items",
                "Live counter",
                "Serving staff")));
        packageRow.setCreatedAt(Instant.now());
        vendorPackageRepository.save(packageRow);
    }

    private void ensureVendorMedia(Vendors vendor, String mediaUrl) {
        String storageKey = "dev/vendors/saffron-leaf-catering.jpg";
        List<VendorMedia> matchingMedia = vendorMediaRepository.findByVendor_Id(vendor.getId())
                .stream()
                .filter(media -> storageKey.equals(media.getStorageKey()) || mediaUrl.equals(media.getMediaUrl()))
                .toList();
        VendorMedia media = matchingMedia.isEmpty() ? new VendorMedia() : matchingMedia.getFirst();

        media.setVendor(vendor);
        media.setMediaUrl(mediaUrl);
        media.setMediaType("IMAGE");
        media.setStorageKey(storageKey);
        media.setFileName("saffron-leaf-catering.jpg");
        media.setCaption("Wedding buffet presentation");
        media.setIsPrimary(true);
        media.setSortOrder(0);
        media.setServiceType(VendorServiceType.CATERING);
        media.setServiceId(vendor.getId());
        if (media.getCreatedAt() == null) {
            media.setCreatedAt(Instant.now());
        }
        vendorMediaRepository.save(media);
        matchingMedia.stream().skip(1).forEach(vendorMediaRepository::delete);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "record";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
