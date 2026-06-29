package com.staminal.venue.admin;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminVendorModerationService {

    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AdminVendorListResponse getVendors(String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        VendorStatus vendorStatus = toVendorStatus(status);

        List<Vendors> filtered = (vendorStatus == null ? vendorRepository.findAll() : vendorRepository.findByStatus(vendorStatus))
                .stream()
                .sorted(Comparator.comparing(Vendors::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        List<AdminVendorResponse> content = filtered.subList(fromIndex, toIndex)
                .stream()
                .map(this::toResponse)
                .toList();

        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / safeSize);
        return new AdminVendorListResponse(content, safePage, safeSize, filtered.size(), totalPages);
    }

    @Transactional
    public AdminVendorResponse reviewVendor(
            String vendorId,
            AdminReviewRequest request,
            Authentication authentication) {
        Vendors vendor = findVendor(vendorId);
        VendorStatus decision = reviewDecision(request);

        if (decision == VendorStatus.REJECTED && !hasText(request.reason())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        }

        if (vendor.getStatus() != VendorStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending vendor profiles can be reviewed");
        }

        User reviewer = currentAdmin(authentication).orElse(null);
        vendor.setStatus(decision);
        vendor.setRejectionReason(decision == VendorStatus.REJECTED ? request.reason().trim() : null);
        vendor.setReviewedByUser(reviewer);
        vendor.setReviewedAt(Instant.now());
        vendor.setUpdatedAt(Instant.now());

        return toResponse(vendorRepository.save(vendor));
    }

    private Vendors findVendor(String vendorId) {
        Long numericId = tryParseLong(vendorId);
        if (numericId != null) {
            return vendorRepository.findById(numericId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));
        }

        String expectedSlug = slugify(vendorId);
        return vendorRepository.findAll()
                .stream()
                .filter(vendor -> slugify(vendor.getBusinessName()).equals(expectedSlug))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));
    }

    private AdminVendorResponse toResponse(Vendors vendor) {
        return new AdminVendorResponse(
                String.valueOf(vendor.getId()),
                firstText(vendor.getBusinessName(), vendor.getVendorName(), "Vendor"),
                firstText(vendor.getVendorName(), vendor.getUser() == null ? null : vendor.getUser().getFullName(), "Vendor"),
                category(vendor),
                firstText(vendor.getCity(), ""),
                firstNonNull(vendor.getReviewedAt(), vendor.getUpdatedAt(), vendor.getCreatedAt()),
                toModerationStatus(vendor.getStatus()),
                vendor.getRejectionReason(),
                vendor.getReviewedByUser() == null ? null : vendor.getReviewedByUser().getFullName(),
                vendor.getReviewedAt());
    }

    private Optional<User> currentAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        String principal = authentication.getName();
        Long userId = tryParseLong(principal);
        if (userId != null) {
            return userRepository.findById(userId);
        }
        return userRepository.findByEmail(principal);
    }

    private VendorStatus reviewDecision(AdminReviewRequest request) {
        String decision = request == null || request.decision() == null
                ? ""
                : request.decision().trim().toUpperCase(Locale.ROOT);
        return switch (decision) {
            case "APPROVED" -> VendorStatus.APPROVED;
            case "REJECTED" -> VendorStatus.REJECTED;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED");
        };
    }

    private VendorStatus toVendorStatus(String status) {
        if (!hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DRAFT" -> VendorStatus.DRAFT;
            case "PENDING", "PENDING_APPROVAL", "SUBMITTED" -> VendorStatus.PENDING;
            case "APPROVED" -> VendorStatus.APPROVED;
            case "REJECTED" -> VendorStatus.REJECTED;
            case "SUSPENDED", "BLOCKED" -> VendorStatus.BLOCKED;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported vendor status");
        };
    }

    private String toModerationStatus(VendorStatus status) {
        if (status == VendorStatus.PENDING) {
            return "PENDING_APPROVAL";
        }
        if (status == VendorStatus.BLOCKED) {
            return "REJECTED";
        }
        return status == null ? "PENDING_APPROVAL" : status.name();
    }

    private String category(Vendors vendor) {
        Set<VendorCategory> categories = vendor.getCategories() == null ? Set.of() : vendor.getCategories();
        return categories.stream()
                .map(VendorCategory::getCategoryName)
                .filter(this::hasText)
                .findFirst()
                .orElse("Service");
    }

    private Long tryParseLong(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String slugify(String value) {
        String normalized = Normalizer.normalize(firstText(value, ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
