package com.staminal.venue.admin;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.halls.Entity.HallMedia;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Dto.UpdateHallRequest;
import com.staminal.venue.halls.Repository.HallMediaRepository;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.halls.Service.HallsService;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminHallModerationService {

    private final HallRepository hallRepository;
    private final HallMediaRepository hallMediaRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final AuditService auditService;
    private final HallsService hallsService;

    @Transactional(readOnly = true)
    public AdminHallListResponse getHalls(String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        HallStatus hallStatus = toHallStatus(status);

        List<Halls> filtered = (hallStatus == HallStatus.PENDING_APPROVAL
                ? Stream.concat(hallRepository.findByStatus(HallStatus.PENDING_APPROVAL).stream(), hallRepository.findByPendingUpdatePayloadIsNotNull().stream()).distinct().toList()
                : hallStatus == null ? hallRepository.findAll() : hallRepository.findByStatus(hallStatus))
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

        boolean pendingProfile = hall.getStatus() == HallStatus.PENDING_APPROVAL;
        boolean pendingUpdate = hall.getStatus() == HallStatus.APPROVED && hall.getPendingUpdatePayload() != null;
        if (!pendingProfile && !pendingUpdate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending hall listings can be reviewed");
        }

        Reviewer reviewer = currentAdmin(authentication);
        LocalDateTime reviewedAt = LocalDateTime.now();

        if (pendingUpdate) {
            if (decision == HallStatus.APPROVED) hallsService.approvePendingUpdate(hall);
            else hallsService.rejectPendingUpdate(hall);
        } else {
            hall.setStatus(decision);
            hall.setRejectionReason(decision == HallStatus.REJECTED ? request.reason().trim() : null);
        }
        hall.setApprovedBy(reviewer.legacyAdmin().orElse(null));
        hall.setApprovedAt(reviewedAt);
        hall.setUpdatedAt(reviewedAt);

        Halls savedHall = hallRepository.save(hall);

        Map<String, Object> newValues = new HashMap<>();
        newValues.put("status", savedHall.getStatus().name());

        if (savedHall.getRejectionReason() != null) {
            newValues.put("reason", savedHall.getRejectionReason());
        }

        auditService.record(
                new AuditCommand(
                        reviewer == null ? null : reviewer.legacyAdmin().map(Admin::getId).orElse(null),
                        "ADMIN",
                        decision == HallStatus.APPROVED
                                ? AuditAction.HALL_APPROVED
                                : AuditAction.HALL_REJECTED,
                        "HALL",
                        String.valueOf(savedHall.getId()),
                        decision == HallStatus.APPROVED
                                ? "Hall approved"
                                : "Hall rejected",
                        null,
                        newValues,
                        null));

        return toResponse(savedHall, reviewer.displayName());
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
        UpdateHallRequest pending = hallsService.pendingUpdateFor(hall);
        Long reviewedBy = hall.getApprovedBy() == null
                ? null
                : hall.getApprovedBy().getId();
        List<String> imageUrls = imageUrls(hall);
        String imageUrl = imageUrls.isEmpty() ? "" : imageUrls.getFirst();

        return new AdminHallResponse(
                String.valueOf(hall.getId()),
                firstText(pending == null ? null : pending.getName(), hall.getName(), "Hall"),
                firstText(hall.getOwnerName(),
                        hall.getOwnerUserId() == null ? null : hall.getOwnerUserId().getFullName(), "Owner"),
                firstText(hall.getOwnerUserId() == null ? null : hall.getOwnerUserId().getPhone(),
                        hall.getContactNumber(), ""),
                pending == null ? location(hall) : location(pending, hall),
                firstText(pending == null ? null : pending.getVenueType(), hall.getHallType(), "Venue"),
                pending == null ? hall.getCapacityMax() : firstNonNull(pending.getCapacityMax(), pending.getCapacity(), hall.getCapacityMax()),
                pending == null ? firstNonNull(hall.getFullDayAmount(), hall.getEveningAmount(), hall.getMorningAmount()) : firstNonNull(pending.getStartingPrice(), hall.getFullDayAmount(), hall.getEveningAmount(), hall.getMorningAmount()),
                hall.getCreatedAt(),
                hall.getUpdatedAt(),
                imageUrl,
                imageUrls,
                hall.getPendingUpdatePayload() != null ? "PENDING_APPROVAL" : toModerationStatus(hall.getStatus()),
                hall.getRejectionReason(),
                reviewedBy,
                hall.getApprovedAt());
    }

    private List<String> imageUrls(Halls hall) {
        Stream<String> coverImage = hasText(hall.getCoverImageUrl())
                ? Stream.of(hall.getCoverImageUrl().trim())
                : Stream.empty();
        Stream<String> galleryImages = hallMediaRepository.findByHallId_Id(hall.getId())
                .stream()
                .sorted(Comparator
                        .comparing((HallMedia media) -> !Boolean.TRUE.equals(media.getIsPrimary()))
                        .thenComparing(media -> media.getSortOrder() == null ? Integer.MAX_VALUE : media.getSortOrder())
                        .thenComparing(media -> media.getId() == null ? Long.MAX_VALUE : media.getId()))
                .map(HallMedia::getUrl)
                .filter(this::hasText)
                .map(String::trim);

        return Stream.concat(coverImage, galleryImages)
                .distinct()
                .toList();
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
            default ->
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED");
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

    private String location(UpdateHallRequest pending, Halls hall) {
        String area = firstText(pending.getArea(), hall.getArea(), "");
        String city = firstText(pending.getCity(), hall.getCity(), "");
        if (!hasText(area)) return city;
        if (!hasText(city)) return area;
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
