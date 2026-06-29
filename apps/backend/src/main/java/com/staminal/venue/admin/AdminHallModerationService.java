package com.staminal.venue.admin;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminHallModerationService {

    private final HallRepository hallRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    @Transactional(readOnly = true)
    public AdminHallListResponse getHalls(String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        HallStatus hallStatus = toHallStatus(status);

        List<Halls> filtered = (hallStatus == null ? hallRepository.findAll() : hallRepository.findByStatus(hallStatus))
                .stream()
                .sorted(Comparator.comparing(Halls::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        List<AdminHallResponse> content = filtered.subList(fromIndex, toIndex)
                .stream()
                .map(hall -> toResponse(hall, null))
                .toList();

        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / safeSize);
        return new AdminHallListResponse(content, safePage, safeSize, filtered.size(), totalPages);
    }

    @Transactional
    public AdminHallResponse reviewHall(
            String hallId,
            AdminReviewRequest request,
            Authentication authentication) {

        Halls hall = findHall(hallId);
        HallStatus decision = reviewDecision(request);

        if (decision == HallStatus.REJECTED && !hasText(request.reason())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        }

        if (hall.getStatus() != HallStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending hall listings can be reviewed");
        }

        Reviewer reviewer = currentAdmin(authentication);
        LocalDateTime reviewedAt = LocalDateTime.now();

        hall.setStatus(decision);
        hall.setRejectionReason(decision == HallStatus.REJECTED ? request.reason().trim() : null);
        hall.setApprovedBy(reviewer.legacyAdmin().orElse(null));
        hall.setApprovedAt(reviewedAt);
        hall.setUpdatedAt(reviewedAt);

        return toResponse(hallRepository.save(hall), reviewer.displayName());
    }

    private Halls findHall(String hallId) {
        Long numericId = tryParseLong(hallId);
        if (numericId != null) {
            return hallRepository.findById(numericId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"));
        }

        String expectedSlug = slugify(hallId);
        return hallRepository.findAll()
                .stream()
                .filter(hall -> slugify(hall.getName()).equals(expectedSlug))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"));
    }

    private AdminHallResponse toResponse(Halls hall, String reviewerOverride) {
        String reviewedBy = firstText(
                reviewerOverride,
                hall.getApprovedBy() == null ? null : hall.getApprovedBy().getFullName());

        return new AdminHallResponse(
                String.valueOf(hall.getId()),
                firstText(hall.getName(), "Hall"),
                firstText(hall.getOwnerName(), hall.getOwnerUserId() == null ? null : hall.getOwnerUserId().getFullName(), "Owner"),
                firstText(hall.getOwnerUserId() == null ? null : hall.getOwnerUserId().getPhone(), hall.getContactNumber(), ""),
                location(hall),
                firstText(hall.getHallType(), "Venue"),
                hall.getCapacityMax(),
                firstNonNull(hall.getFullDayAmount(), hall.getEveningAmount(), hall.getMorningAmount()),
                hall.getCreatedAt(),
                hall.getUpdatedAt(),
                firstText(hall.getCoverImageUrl(), ""),
                toModerationStatus(hall.getStatus()),
                hall.getRejectionReason(),
                reviewedBy,
                hall.getApprovedAt());
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
            return new Reviewer(user.getFullName(), legacyAdmin);
        }

        Optional<Admin> legacyAdmin = adminRepository.findByEmail(principal);
        Optional<User> user = userRepository.findByEmail(principal);
        if (legacyAdmin.isEmpty() && user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin session is invalid");
        }

        return new Reviewer(
                user.map(User::getFullName)
                        .orElseGet(() -> legacyAdmin.map(Admin::getFullName).orElse("Admin")),
                legacyAdmin);
    }

    private HallStatus reviewDecision(AdminReviewRequest request) {
        String decision = request == null || request.decision() == null
                ? ""
                : request.decision().trim().toUpperCase(Locale.ROOT);
        return switch (decision) {
            case "APPROVED" -> HallStatus.APPROVED;
            case "REJECTED" -> HallStatus.REJECTED;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED");
        };
    }

    private HallStatus toHallStatus(String status) {
        if (!hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DRAFT" -> HallStatus.DRAFT;
            case "PENDING", "PENDING_APPROVAL", "SUBMITTED" -> HallStatus.PENDING_APPROVAL;
            case "APPROVED" -> HallStatus.APPROVED;
            case "REJECTED" -> HallStatus.REJECTED;
            case "SUSPENDED", "BLOCKED" -> HallStatus.BLOCKED;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported hall status");
        };
    }

    private String toModerationStatus(HallStatus status) {
        if (status == HallStatus.PENDING_APPROVAL) {
            return "PENDING_APPROVAL";
        }
        if (status == HallStatus.BLOCKED) {
            return "REJECTED";
        }
        return status == null ? "PENDING_APPROVAL" : status.name();
    }

    private String location(Halls hall) {
        String area = firstText(hall.getArea(), "");
        String city = firstText(hall.getCity(), "");
        if (!hasText(area)) {
            return city;
        }
        if (!hasText(city)) {
            return area;
        }
        return area + ", " + city;
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

    private record Reviewer(String displayName, Optional<Admin> legacyAdmin) {
    }
}
