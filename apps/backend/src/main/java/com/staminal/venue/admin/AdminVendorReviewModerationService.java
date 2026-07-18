package com.staminal.venue.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.reviews.HallReview.ReviewModerationStatus;
import com.staminal.venue.reviews.VendorReview.VendorReview;
import com.staminal.venue.reviews.VendorReview.VendorReviewRepository;
import com.staminal.venue.reviews.VendorReview.VendorRatingAggregateService;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminVendorReviewModerationService {

    private final VendorReviewRepository vendorReviewRepository;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final VendorRatingAggregateService vendorRatingAggregateService;

    @Transactional(readOnly = true)
    public AdminVendorReviewListResponse getReviews(String status, int page, int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        Set<ReviewModerationStatus> statuses = toStatusFilter(status);

        List<VendorReview> filtered = statuses == null
                ? vendorReviewRepository.findAll()
                : vendorReviewRepository.findByModerationStatusIn(statuses);

        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());

        List<AdminVendorReviewResponse> content = filtered.subList(fromIndex, toIndex)
                .stream()
                .map(this::toResponse)
                .toList();

        int totalPages = filtered.isEmpty()
                ? 0
                : (int) Math.ceil((double) filtered.size() / safeSize);

        return new AdminVendorReviewListResponse(
                content,
                safePage,
                safeSize,
                filtered.size(),
                totalPages);
    }

    @Transactional
    public AdminVendorReviewResponse moderateReview(
            String reviewId,
            AdminReviewModerationRequest request,
            Authentication authentication) {

        VendorReview review = findReview(reviewId);

        ReviewModerationStatus previousStatus = safeStatus(review.getModerationStatus());

        ReviewModerationStatus nextStatus = moderationDecision(request);

        if ((nextStatus == ReviewModerationStatus.HIDDEN
                || nextStatus == ReviewModerationStatus.REJECTED)
                && !hasText(request.reason())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reason is required");
        }

        Reviewer reviewer = currentAdmin(authentication);

        Instant moderatedAt = Instant.now();

        review.setModerationStatus(nextStatus);
        review.setModerationReason(
                hasText(request.reason())
                        ? request.reason().trim()
                        : null);

        review.setModeratedByAdmin(
                reviewer.legacyAdmin().orElse(null));

        review.setModeratedAt(moderatedAt);

        review.setActive(nextStatus == ReviewModerationStatus.PUBLISHED);

        VendorReview saved = vendorReviewRepository.save(review);

        vendorRatingAggregateService.refreshForVendorId(
                saved.getVendor().getId());

        auditService.record(
                new AuditCommand(
                        reviewer.actorUserId(),
                        "ADMIN",
                        nextStatus == ReviewModerationStatus.PUBLISHED
                                ? AuditAction.REVIEW_APPROVED
                                : AuditAction.REVIEW_HIDDEN,
                        "VENDOR_REVIEW",
                        String.valueOf(saved.getId()),
                        nextStatus == ReviewModerationStatus.PUBLISHED
                                ? "Vendor review published"
                                : "Vendor review hidden",
                        Map.of("status", previousStatus.name()),
                        auditNewValues(saved),
                        null));

        return toResponse(saved);
    }

    private VendorReview findReview(String reviewId) {

        Long id = tryParseLong(reviewId);

        if (id == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid review id");
        }

        return vendorReviewRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Review not found"));
    }

    private AdminVendorReviewResponse toResponse(VendorReview review) {

        return new AdminVendorReviewResponse(

                String.valueOf(review.getId()),

                review.getVendor().getBusinessName(),

                review.getCustomer() == null
                        ? "Customer"
                        : review.getCustomer().getFullName(),

                review.getRating(),

                review.getComment(),

                firstText(review.getModerationReason(), ""),

                true,

                safeStatus(review.getModerationStatus()).name(),

                review.getCreatedAt(),

                review.getModeratedByAdmin() == null
                        ? null
                        : review.getModeratedByAdmin().getId(),

                review.getModeratedAt());
    }

    private Map<String, Object> auditNewValues(VendorReview review) {

        Map<String, Object> values = new HashMap<>();

        values.put(
                "status",
                safeStatus(review.getModerationStatus()).name());

        if (hasText(review.getModerationReason())) {
            values.put(
                    "reason",
                    review.getModerationReason());
        }

        return values;
    }

    private ReviewModerationStatus safeStatus(ReviewModerationStatus status) {
        return status == null ? ReviewModerationStatus.PENDING : status;
    }

    private ReviewModerationStatus moderationDecision(AdminReviewModerationRequest request) {
        if (request == null || request.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        return switch (request.status()) {
            case PUBLISHED -> ReviewModerationStatus.PUBLISHED;
            case HIDDEN -> ReviewModerationStatus.HIDDEN;
            case REJECTED -> ReviewModerationStatus.REJECTED;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Status must be PUBLISHED, HIDDEN, or REJECTED");
        };
    }

    private Set<ReviewModerationStatus> toStatusFilter(String status) {
        if (!hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PENDING" -> Set.of(ReviewModerationStatus.PENDING);
            case "REPORTED" -> Set.of(ReviewModerationStatus.PENDING, ReviewModerationStatus.REPORTED);
            case "PUBLISHED", "APPROVED" -> Set.of(ReviewModerationStatus.PUBLISHED);
            case "HIDDEN" -> Set.of(ReviewModerationStatus.HIDDEN);
            case "REJECTED" -> Set.of(ReviewModerationStatus.REJECTED);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported review status");
        };
    }

    private Reviewer currentAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }

        String principal = authentication.getName();
        Long userId = tryParseLong(principal);
        if (userId != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(
                            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin session is invalid"));
            Optional<Admin> legacyAdmin = hasText(user.getEmail())
                    ? adminRepository.findByEmail(user.getEmail())
                    : Optional.empty();
            return new Reviewer(user.getId(), legacyAdmin);
        }

        Optional<Admin> legacyAdmin = adminRepository.findByEmail(principal);
        Optional<User> user = userRepository.findByEmail(principal);
        if (legacyAdmin.isEmpty() && user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin session is invalid");
        }

        return new Reviewer(user.map(User::getId).orElseGet(() -> legacyAdmin.map(Admin::getId).orElse(null)),
                legacyAdmin);
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

    private record Reviewer(Long actorUserId, Optional<Admin> legacyAdmin) {
    }

}