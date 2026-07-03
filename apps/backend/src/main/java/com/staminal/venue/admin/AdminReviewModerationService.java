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
import com.staminal.venue.reviews.Review;
import com.staminal.venue.reviews.ReviewModerationStatus;
import com.staminal.venue.reviews.ReviewRepository;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminReviewModerationService {

    private final ReviewRepository reviewRepository;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public AdminReviewListResponse getReviews(String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        Set<ReviewModerationStatus> statuses = toStatusFilter(status);
        List<Review> filtered = statuses == null
                ? reviewRepository.findAllForAdmin()
                : reviewRepository.findForAdminByStatuses(statuses);

        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        List<AdminReviewResponse> content = filtered.subList(fromIndex, toIndex)
                .stream()
                .map(this::toResponse)
                .toList();

        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / safeSize);
        return new AdminReviewListResponse(content, safePage, safeSize, filtered.size(), totalPages);
    }

    @Transactional
    public AdminReviewResponse moderateReview(
            String reviewId,
            AdminReviewModerationRequest request,
            Authentication authentication) {
        Review review = findReview(reviewId);
        ReviewModerationStatus previousStatus = safeStatus(review.getModerationStatus());
        ReviewModerationStatus nextStatus = moderationDecision(request);

        if ((nextStatus == ReviewModerationStatus.HIDDEN || nextStatus == ReviewModerationStatus.REJECTED)
                && !hasText(request.reason())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason is required");
        }

        Reviewer reviewer = currentAdmin(authentication);
        Instant moderatedAt = Instant.now();

        review.setModerationStatus(nextStatus);
        review.setModerationReason(hasText(request.reason()) ? request.reason().trim() : null);
        review.setModeratedByAdmin(reviewer.legacyAdmin().orElse(null));
        review.setModeratedAt(moderatedAt);
        review.setActive(nextStatus == ReviewModerationStatus.PUBLISHED);

        Review savedReview = reviewRepository.save(review);

        auditService.record(new AuditCommand(
                reviewer.actorUserId(),
                "ADMIN",
                nextStatus == ReviewModerationStatus.PUBLISHED
                        ? AuditAction.REVIEW_APPROVED
                        : AuditAction.REVIEW_HIDDEN,
                "REVIEW",
                String.valueOf(savedReview.getId()),
                nextStatus == ReviewModerationStatus.PUBLISHED
                        ? "Review published"
                        : "Review hidden",
                Map.of("status", previousStatus.name()),
                auditNewValues(savedReview),
                null));

        return toResponse(savedReview);
    }

    private Review findReview(String reviewId) {
        Long numericId = tryParseLong(reviewId);
        if (numericId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid review id");
        }
        return reviewRepository.findByIdForAdmin(numericId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
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
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin session is invalid"));
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

        return new Reviewer(user.map(User::getId).orElseGet(() -> legacyAdmin.map(Admin::getId).orElse(null)), legacyAdmin);
    }

    private AdminReviewResponse toResponse(Review review) {
        return new AdminReviewResponse(
                String.valueOf(review.getId()),
                review.getHall() == null || !hasText(review.getHall().getName())
                        ? "Venue"
                        : review.getHall().getName(),
                review.getCustomer() == null || !hasText(review.getCustomer().getFullName())
                        ? "Customer"
                        : review.getCustomer().getFullName(),
                review.getRating(),
                review.getComment(),
                firstText(review.getReportReason(), review.getModerationReason(), ""),
                review.getVerifiedService(),
                safeStatus(review.getModerationStatus()).name(),
                review.getCreatedAt(),
                review.getModeratedByAdmin() == null ? null : review.getModeratedByAdmin().getId(),
                review.getModeratedAt());
    }

    private ReviewModerationStatus safeStatus(ReviewModerationStatus status) {
        return status == null ? ReviewModerationStatus.PENDING : status;
    }

    private Map<String, Object> auditNewValues(Review review) {
        Map<String, Object> values = new HashMap<>();
        values.put("status", safeStatus(review.getModerationStatus()).name());
        if (hasText(review.getModerationReason())) {
            values.put("reason", review.getModerationReason());
        }
        return values;
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
