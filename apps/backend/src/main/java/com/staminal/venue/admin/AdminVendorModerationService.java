package com.staminal.venue.admin;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.vendors.Dto.VendorResponse;
import com.staminal.venue.vendors.Dto.UpdateVendorRequest;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.VendorMedia;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorMediaRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;
import com.staminal.venue.vendors.Service.VendorService;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminVendorModerationService {

    private final VendorRepository vendorRepository;
    private final VendorMediaRepository vendorMediaRepository;
    private final AuditService auditService;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final VendorService vendorService;

    @Transactional(readOnly = true)
    public AdminVendorListResponse getVendors(String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        VendorStatus vendorStatus = toVendorStatus(status);

        List<Vendors> filtered = (vendorStatus == VendorStatus.PENDING
                ? java.util.stream.Stream.concat(vendorRepository.findByStatus(VendorStatus.PENDING).stream(), vendorRepository.findByPendingUpdatePayloadIsNotNull().stream()).distinct().toList()
                : vendorStatus == null ? vendorRepository.findAll() : vendorRepository.findByStatus(vendorStatus))
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

        boolean pendingProfile = vendor.getStatus() == VendorStatus.PENDING;
        boolean pendingUpdate = vendor.getStatus() == VendorStatus.APPROVED && vendor.getPendingUpdatePayload() != null;
        if (!pendingProfile && !pendingUpdate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending vendor profiles can be reviewed");
        }

        Admin reviewer = currentAdmin(authentication).orElse(null);
        if (pendingUpdate) {
            if (decision == VendorStatus.APPROVED) vendorService.approvePendingUpdate(vendor);
            else vendorService.rejectPendingUpdate(vendor);
            vendor = vendorRepository.findById(vendor.getId()).orElseThrow();
        } else {
            vendor.setStatus(decision);
            vendor.setRejectionReason(decision == VendorStatus.REJECTED ? request.reason().trim() : null);
            if (decision == VendorStatus.APPROVED) {
                approveInitialMedia(vendor);
            }
        }
        vendor.setReviewedByAdmin(reviewer);
        vendor.setReviewedAt(Instant.now());
        vendor.setUpdatedAt(Instant.now());

        Vendors savedVendor = vendorRepository.save(vendor);

        Map<String, Object> newValues = new HashMap<>();
        newValues.put("status", savedVendor.getStatus().name());

        if (savedVendor.getRejectionReason() != null) {
            newValues.put("reason", savedVendor.getRejectionReason());
        }

        auditService.record(
                new AuditCommand(
                        reviewer == null ? null : reviewer.getId(),
                        "ADMIN",
                        decision == VendorStatus.APPROVED
                                ? AuditAction.VENDOR_APPROVED
                                : AuditAction.VENDOR_REJECTED,
                        "VENDOR",
                        String.valueOf(savedVendor.getId()),
                        decision == VendorStatus.APPROVED
                                ? "Vendor approved"
                                : "Vendor rejected",
                        null,
                        newValues,
                        null));

        return toResponse(savedVendor);
    }

    private void approveInitialMedia(Vendors vendor) {
        List<VendorMedia> media = vendorMediaRepository.findByVendor_Id(vendor.getId());
        media.forEach(item -> item.setApproved(true));
        vendorMediaRepository.saveAll(media);
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
        UpdateVendorRequest pending = vendorService.pendingUpdateFor(vendor);
        return new AdminVendorResponse(
                String.valueOf(vendor.getId()),
                firstText(pending == null ? null : pending.getBusinessName(), vendor.getBusinessName(), vendor.getVendorName(), "Vendor"),
                firstText(vendor.getVendorName(), vendor.getUser() == null ? null : vendor.getUser().getFullName(),
                        "Vendor"),
                firstText(pending == null ? null : pending.getCategory(), category(vendor)),
                firstText(pending == null ? null : pending.getCity(), vendor.getCity(), ""),
                firstNonNull(vendor.getReviewedAt(), vendor.getUpdatedAt(), vendor.getCreatedAt()),
                vendor.getPendingUpdatePayload() != null ? "PENDING_APPROVAL" : toModerationStatus(vendor.getStatus()),
                vendor.getRejectionReason(),
                vendor.getReviewedByAdmin() == null ? null
                        : vendor.getReviewedByAdmin() == null
                                ? null
                                : vendor.getReviewedByAdmin().getId(),
                vendor.getReviewedAt());
    }

    private Optional<Admin> currentAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        String principal = authentication.getName();
        Long userId = tryParseLong(principal);
        if (userId != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin session is invalid"));
            return hasText(user.getEmail()) ? adminRepository.findByEmail(user.getEmail()) : Optional.empty();
        }

        return adminRepository.findByEmail(principal);
    }

    private VendorStatus reviewDecision(AdminReviewRequest request) {
        String decision = request == null || request.decision() == null
                ? ""
                : request.decision().trim().toUpperCase(Locale.ROOT);
        return switch (decision) {
            case "APPROVED" -> VendorStatus.APPROVED;
            case "REJECTED" -> VendorStatus.REJECTED;
            default ->
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED");
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

    @Transactional(readOnly = true)
    public VendorResponse getVendorDetails(String vendorId) {

        Vendors vendor = findVendor(vendorId);

        VendorResponse response = new VendorResponse();

        response.setId(vendor.getId());
        response.setVendorName(vendor.getVendorName());
        response.setBusinessName(vendor.getBusinessName());

        response.setCategory(
                vendor.getCategories()
                        .stream()
                        .findFirst()
                        .map(VendorCategory::getCategoryName)
                        .orElse(""));
        response.setCategories(
                vendor.getCategories()
                        .stream()
                        .map(VendorCategory::getCategoryName)
                        .collect(Collectors.toSet()));

        response.setEmail(vendor.getEmail());
        response.setDescription(vendor.getDescription());
        response.setCoverImageUrl(vendor.getCoverImageUrl());
        response.setAddressLine(vendor.getAddressLine());
        response.setCity(vendor.getCity());
        response.setArea(vendor.getArea());
        response.setPincode(vendor.getPincode());
        response.setContactNumber(vendor.getContactNumber());
        response.setWhatsAppNumber(vendor.getWhatsAppNumber());
        response.setInstagramUrl(vendor.getInstagramUrl());
        response.setFacebookUrl(vendor.getFacebookUrl());
        response.setWhatsAppUrl(vendor.getWhatsAppUrl());
        response.setYearsInBusiness(vendor.getYearsInBusiness());
        response.setServiceRadius(vendor.getServiceRadius());
        response.setPackageName(vendor.getPackageName());
        response.setStartingPrice(vendor.getStartingPrice());
        response.setPackageDescription(vendor.getPackageDescription());
        response.setServices(vendor.getServices());
        response.setStatus(toModerationStatus(vendor.getStatus()));
        response.setRejectionReason(vendor.getRejectionReason());

        UpdateVendorRequest pending = vendorService.pendingUpdateFor(vendor);
        if (pending != null) {
            response.setBusinessName(firstText(pending.getBusinessName(), response.getBusinessName()));
            response.setCategory(firstText(pending.getCategory(), response.getCategory()));
            response.setDescription(pending.getDescription());
            response.setCoverImageUrl(firstText(pending.getCoverImageUrl(), response.getCoverImageUrl()));
            response.setAddressLine(firstText(pending.getAddressLine(), response.getAddressLine()));
            response.setCity(firstText(pending.getCity(), response.getCity()));
            response.setArea(firstText(pending.getArea(), response.getArea()));
            response.setPincode(firstText(pending.getPincode(), response.getPincode()));
            response.setContactNumber(firstText(pending.getContactNumber(), response.getContactNumber()));
            response.setWhatsAppNumber(firstText(pending.getWhatsAppNumber(), response.getWhatsAppNumber()));
            response.setInstagramUrl(pending.getInstagramUrl());
            response.setFacebookUrl(pending.getFacebookUrl());
            response.setWhatsAppUrl(pending.getWhatsAppUrl());
            response.setYearsInBusiness(pending.getYearsInBusiness());
            response.setServiceRadius(pending.getServiceRadius());
            response.setServices(pending.getServices());
            response.setPackageName(pending.getPackageName());
            response.setStartingPrice(pending.getStartingPrice());
            response.setPackageDescription(pending.getPackageDescription());
            response.setStatus("PENDING_APPROVAL");
        }

        return response;
    }
}
