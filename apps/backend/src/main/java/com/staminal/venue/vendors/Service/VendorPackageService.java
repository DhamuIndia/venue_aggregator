package com.staminal.venue.vendors.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.enums.VendorServiceType;
import com.staminal.venue.vendors.Dto.CreateVendorPackageRequest;
import com.staminal.venue.vendors.Dto.VendorPackageListResponse;
import com.staminal.venue.vendors.Dto.VendorPackageResponse;
import com.staminal.venue.vendors.Dto.VendorPackageUpsertRequest;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.VendorPackage;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorPackageRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorPackageService {

        private final VendorPackageRepository vendorPackageRepository;
        private final VendorRepository vendorRepository;

        @Transactional(readOnly = true)
        public VendorPackageListResponse getMyPackages(Authentication authentication) {
                Long userId = currentUserId(authentication);

                List<VendorPackageResponse> packages = vendorPackageRepository.findByVendor_User_Id(userId)
                                .stream()
                                .sorted(Comparator.comparing(
                                                VendorPackage::getCreatedAt,
                                                Comparator.nullsLast(Comparator.reverseOrder())))
                                .map(this::mapToResponse)
                                .toList();

                return new VendorPackageListResponse(packages);
        }

        @Transactional
        public VendorPackageResponse createMyPackage(
                        VendorPackageUpsertRequest request,
                        Authentication authentication) {

                Vendors vendor = currentVendor(currentUserId(authentication));
                VendorPackage vendorPackage = new VendorPackage();

                vendorPackage.setVendor(vendor);
                vendorPackage.setCreatedAt(Instant.now());
                applyRequest(vendorPackage, vendor, request);

                VendorPackage saved = vendorPackageRepository.save(vendorPackage);
                refreshVendorPackageSummary(vendor, saved);

                return mapToResponse(saved);
        }

        @Transactional
        public VendorPackageResponse updateMyPackage(
                        String packageId,
                        VendorPackageUpsertRequest request,
                        Authentication authentication) {

                Long userId = currentUserId(authentication);
                VendorPackage vendorPackage = findOwnedPackage(parsePackageId(packageId), userId);
                Vendors vendor = vendorPackage.getVendor();

                applyRequest(vendorPackage, vendor, request);

                VendorPackage saved = vendorPackageRepository.save(vendorPackage);
                refreshVendorPackageSummary(vendor, saved);

                return mapToResponse(saved);
        }

        @Transactional
        public void deleteMyPackage(String packageId, Authentication authentication) {
                Long userId = currentUserId(authentication);
                VendorPackage vendorPackage = findOwnedPackage(parsePackageId(packageId), userId);
                Vendors vendor = vendorPackage.getVendor();

                vendorPackageRepository.delete(vendorPackage);
                refreshVendorPackageSummary(vendor, null);
        }

        @Transactional
        public VendorPackageResponse createPackage(
                        CreateVendorPackageRequest request) {

                Vendors vendor = vendorRepository.findById(
                                request.getVendorId())
                                .orElseThrow(() -> new RuntimeException("Vendor not found"));

                VendorPackage vendorPackage = new VendorPackage();

                vendorPackage.setVendor(vendor);
                vendorPackage.setPackageName(request.getPackageName());
                vendorPackage.setDescription(request.getDescription());
                vendorPackage.setPrice(request.getPrice());
                vendorPackage.setCreatedAt(Instant.now());
                vendorPackage.setServiceType(request.getServiceType());
                vendorPackage.setServiceId(request.getServiceId());

                VendorPackage savedVendorPackage = vendorPackageRepository.save(vendorPackage);

                VendorPackageResponse vendorPackageResponse = new VendorPackageResponse();
                vendorPackageResponse.setId(savedVendorPackage.getId());
                vendorPackageResponse.setDescription(savedVendorPackage.getDescription());
                vendorPackageResponse.setPackageName(savedVendorPackage.getPackageName());
                vendorPackageResponse.setPrice(savedVendorPackage.getPrice());
                vendorPackageResponse.setServiceType(
                                savedVendorPackage.getServiceType());
                vendorPackageResponse.setIncludes(savedVendorPackage.getIncludes());

                vendorPackageResponse.setServiceId(
                                savedVendorPackage.getServiceId());
                return vendorPackageResponse;
        }

        @Transactional(readOnly = true)
        public List<VendorPackageResponse> getPackages(Long vendorId) {

                List<VendorPackage> packages = vendorPackageRepository.findByVendor_Id(vendorId);

                return packages.stream()
                                .map(this::mapToResponse)
                                .toList();
        }

        private void applyRequest(
                        VendorPackage vendorPackage,
                        Vendors vendor,
                        VendorPackageUpsertRequest request) {

                if (request == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Package details are required");
                }

                String packageName = firstText(request.name(), request.packageName());
                BigDecimal price = request.price();

                if (packageName == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Package name is required");
                }
                if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Package price must be greater than zero");
                }

                vendorPackage.setPackageName(packageName);
                vendorPackage.setDescription(trimToNull(request.description()));
                vendorPackage.setPrice(price);
                vendorPackage.setIncludes(normalizedIncludes(request.includes()));
                vendorPackage.setServiceType(serviceTypeFor(vendor));
                vendorPackage.setServiceId(vendor.getId());
        }

        private VendorPackage findOwnedPackage(Long packageId, Long userId) {
                return vendorPackageRepository.findByIdAndVendor_User_Id(packageId, userId)
                                .orElseThrow(() -> {
                                        if (vendorPackageRepository.findById(packageId).isPresent()) {
                                                return new ResponseStatusException(
                                                                HttpStatus.FORBIDDEN,
                                                                "Package does not belong to the logged-in vendor");
                                        }
                                        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found");
                                });
        }

        private Vendors currentVendor(Long userId) {
                return vendorRepository.findByUserId(userId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Create vendor profile before managing packages"));
        }

        private Long currentUserId(Authentication authentication) {
                if (authentication == null || !authentication.isAuthenticated()) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
                }
                try {
                        return Long.parseLong(authentication.getName());
                } catch (NumberFormatException exception) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
                }
        }

        private Long parsePackageId(String packageId) {
                try {
                        return Long.parseLong(packageId);
                } catch (NumberFormatException exception) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found");
                }
        }

        private List<String> normalizedIncludes(List<String> includes) {
                if (includes == null) {
                        return new ArrayList<>();
                }
                if (includes.size() > 20) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Package can have up to 20 inclusions");
                }

                List<String> normalized = new ArrayList<>();
                for (String include : includes) {
                        String trimmed = trimToNull(include);
                        if (trimmed == null) {
                                continue;
                        }
                        if (trimmed.length() > 180) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "Package inclusions must be 180 characters or less");
                        }
                        normalized.add(trimmed);
                }
                return normalized;
        }

        private void refreshVendorPackageSummary(Vendors vendor, VendorPackage preferredPackage) {
                if (vendor == null) {
                        return;
                }

                List<VendorPackage> packages = vendor.getId() == null
                                ? List.of()
                                : vendorPackageRepository.findByVendor_Id(vendor.getId());
                if (packages.isEmpty() && preferredPackage != null) {
                        packages = List.of(preferredPackage);
                }

                packages.stream()
                                .filter(packageItem -> packageItem.getPrice() != null)
                                .min(Comparator.comparing(VendorPackage::getPrice))
                                .ifPresentOrElse(lowest -> {
                                        vendor.setPackageName(lowest.getPackageName());
                                        vendor.setPackageDescription(lowest.getDescription());
                                        vendor.setStartingPrice(lowest.getPrice());
                                }, () -> {
                                        vendor.setPackageName(null);
                                        vendor.setPackageDescription(null);
                                        vendor.setStartingPrice(null);
                                });

                vendor.setUpdatedAt(Instant.now());
                vendorRepository.save(vendor);
        }

        private VendorPackageResponse mapToResponse(VendorPackage vendorPackage) {
                VendorPackageResponse response = new VendorPackageResponse();

                response.setId(vendorPackage.getId());
                response.setPackageName(vendorPackage.getPackageName());
                response.setDescription(vendorPackage.getDescription());
                response.setPrice(vendorPackage.getPrice());
                response.setServiceType(vendorPackage.getServiceType());
                response.setServiceId(vendorPackage.getServiceId());
                response.setIncludes(vendorPackage.getIncludes() == null
                                ? List.of()
                                : vendorPackage.getIncludes());

                return response;
        }

        private VendorServiceType serviceTypeFor(Vendors vendor) {
                String categoryText = Set.copyOf(vendor.getCategories() == null ? Set.of() : vendor.getCategories())
                                .stream()
                                .map(VendorCategory::getCategoryName)
                                .filter(value -> value != null && !value.isBlank())
                                .findFirst()
                                .orElse("");
                String serviceText = vendor.getServices() == null ? "" : String.join(" ", vendor.getServices());
                String normalized = (categoryText + " " + serviceText).toUpperCase(Locale.ROOT);

                if (normalized.contains("PHOTO")) {
                        return VendorServiceType.PHOTOGRAPHY;
                }
                if (normalized.contains("MAKEUP") || normalized.contains("MEHENDI")) {
                        return VendorServiceType.MAKEUP;
                }
                if (normalized.contains("DJ") || normalized.contains("MUSIC")) {
                        return VendorServiceType.DJ;
                }
                if (normalized.contains("DECOR")) {
                        return VendorServiceType.DECORATION;
                }
                return VendorServiceType.CATERING;
        }

        private String firstText(String... values) {
                for (String value : values) {
                        String trimmed = trimToNull(value);
                        if (trimmed != null) {
                                return trimmed;
                        }
                }
                return null;
        }

        private String trimToNull(String value) {
                if (value == null) {
                        return null;
                }
                String trimmed = value.trim();
                return trimmed.isEmpty() ? null : trimmed;
        }
}
