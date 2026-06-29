package com.staminal.venue.halls.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.halls.Dto.CreateHallRequest;
import com.staminal.venue.halls.Dto.HallListResponse;
import com.staminal.venue.halls.Dto.HallResponse;
import com.staminal.venue.halls.Dto.Pricing;
import com.staminal.venue.halls.Dto.UpdateHallRequest;
import com.staminal.venue.halls.Entity.HallMedia;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallMediaRepository;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HallsService {

    private final HallRepository hallRepository;
    private final UserRepository userRepository;
    private final HallMediaRepository hallMediaRepository;

    public HallResponse createHall(CreateHallRequest request, Authentication authentication) {
        User owner = currentUser(authentication);

        Halls hall = new Halls();
        applyRequest(hall, request);
        hall.setOwnerUserId(owner);
        hall.setOwnerName(owner.getFullName());
        hall.setRatings(0.0);
        hall.setStatus(HallStatus.DRAFT);
        hall.setCreatedAt(LocalDateTime.now());
        hall.setUpdatedAt(LocalDateTime.now());

        return mapToResponse(hallRepository.save(hall), false);
    }

    public List<HallResponse> getMyHalls(Authentication authentication) {
        Long userId = currentUserId(authentication);
        return hallRepository.findByOwnerUserId_Id(userId)
                .stream()
                .map(hall -> mapToResponse(hall, false))
                .toList();
    }

    public HallResponse getHall(String hallId, Authentication authentication) {
        return mapToResponse(findOwnedHall(hallId, authentication), true);
    }

    public HallResponse updateHall(
            String hallId,
            UpdateHallRequest request,
            Authentication authentication) {
        Halls hall = findOwnedHall(hallId, authentication);
        applyRequest(hall, request);
        hall.setStatus(HallStatus.DRAFT);
        hall.setRejectionReason(null);
        hall.setUpdatedAt(LocalDateTime.now());

        return mapToResponse(hallRepository.save(hall), false);
    }

    public HallResponse submitHall(String hallId, Authentication authentication) {

        Halls hall = findOwnedHall(hallId, authentication);

        if (hall.getStatus() == HallStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Approved hall is already live");
        }

        validateHallForSubmission(hall);

        hall.setStatus(HallStatus.PENDING_APPROVAL);
        hall.setRejectionReason(null);
        hall.setUpdatedAt(LocalDateTime.now());

        return mapToResponse(hallRepository.save(hall), false);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void validateHallForSubmission(Halls hall) {

        if (isBlank(hall.getName())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Hall name is required");
        }

        if (isBlank(hall.getDescription())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Description is required");
        }

        if (isBlank(hall.getAddressLine())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Address is required");
        }

        if (isBlank(hall.getCity())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "City is required");
        }

        if (isBlank(hall.getArea())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Area is required");
        }

        if (hall.getCapacityMax() == null || hall.getCapacityMax() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Capacity is required");
        }

        if (isBlank(hall.getHallType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Venue type is required");
        }

        if (isBlank(hall.getContactNumber())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Contact number is required");
        }

        if (hall.getMorningAmount() == null
                && hall.getEveningAmount() == null
                && hall.getFullDayAmount() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Pricing is required");
        }

        if (isBlank(hall.getCoverImageUrl())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cover image is required");
        }
    }

    public HallListResponse searchPublicHalls(
            String q,
            String city,
            String area,
            String venueType,
            Integer minCapacity,
            BigDecimal maxPrice,
            String sort,
            int page,
            int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        List<Halls> filtered = hallRepository.findByStatus(HallStatus.APPROVED)
                .stream()
                .filter(hall -> matchesText(hall, q))
                .filter(hall -> matchesEquals(hall.getCity(), city))
                .filter(hall -> matchesEquals(hall.getArea(), area))
                .filter(hall -> matchesVenueType(hall, venueType))
                .filter(hall -> minCapacity == null || capacity(hall) >= minCapacity)
                .filter(hall -> maxPrice == null || startingPrice(hall) == null
                        || startingPrice(hall).compareTo(maxPrice) <= 0)
                .sorted(publicSort(sort))
                .toList();

        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        List<HallResponse> content = filtered.subList(fromIndex, toIndex)
                .stream()
                .map(hall -> mapToResponse(hall, true))
                .toList();

        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / safeSize);
        return new HallListResponse(content, safePage, safeSize, filtered.size(), totalPages);
    }

    public HallResponse getPublicHall(String hallId) {
        Halls hall = findApprovedHall(hallId);
        return toPublicResponse(hall);
    }

    public HallResponse toPublicResponse(Halls hall) {
        return mapToResponse(hall, true);
    }

    private void applyRequest(Halls hall, CreateHallRequest request) {
        hall.setName(request.getName());
        hall.setDescription(request.getDescription());
        hall.setAddressLine(request.getAddressLine());
        hall.setCity(request.getCity());
        hall.setArea(request.getArea());
        hall.setPincode(request.getPincode());
        hall.setLatitude(request.getLatitude());
        hall.setLongitude(request.getLongitude());
        hall.setCapacityMin(request.getCapacity());
        hall.setCapacityMax(firstNonNull(request.getCapacityMax(), request.getCapacity()));
        hall.setFloors(request.getFloors());
        hall.setAcAvailable(request.getAcAvailable());
        hall.setHallType(request.getVenueType());
        hall.setRooms(request.getRooms());
        hall.setCarParking(request.getCarParking());
        hall.setBikeParking(request.getBikeParking());
        hall.setDiningAvailable(request.getDiningAvailable());
        hall.setDiningCapacity(request.getDiningCapacity());
        hall.setGeneratorAvailable(request.getGeneratorAvailable());
        hall.setLiftAvailable(request.getLiftAvailable());
        hall.setContactNumber(request.getContactNumber());
        hall.setWhatsappNumber(request.getWhatsappNumber());
        hall.setCoverImageUrl(request.getCoverImageUrl() != null ? request.getCoverImageUrl() : "");
        hall.setBridalRoomAvailable(request.getBridalRoomAvailable());
        hall.setCateringKitchenAvailable(request.getCateringKitchenAvailable());

        if (request.getPricing() != null) {
            hall.setMorningAmount(request.getPricing().getMorningPrice());
            hall.setEveningAmount(request.getPricing().getEveningPrice());
            hall.setFullDayAmount(request.getPricing().getFullDayPrice());
        } else if (request.getStartingPrice() != null) {
            hall.setFullDayAmount(request.getStartingPrice());
        }
    }

    private void applyRequest(Halls hall, UpdateHallRequest request) {
        hall.setName(request.getName());
        hall.setDescription(request.getDescription());
        hall.setAddressLine(request.getAddressLine());
        hall.setCity(request.getCity());
        hall.setArea(request.getArea());
        hall.setPincode(request.getPincode());
        hall.setLatitude(request.getLatitude());
        hall.setLongitude(request.getLongitude());
        hall.setCapacityMin(request.getCapacity());
        hall.setCapacityMax(firstNonNull(request.getCapacityMax(), request.getCapacity()));
        hall.setFloors(request.getFloors());
        hall.setAcAvailable(request.getAcAvailable());
        hall.setHallType(request.getVenueType());
        hall.setRooms(request.getRooms());
        hall.setCarParking(request.getCarParking());
        hall.setBikeParking(request.getBikeParking());
        hall.setDiningAvailable(request.getDiningAvailable());
        hall.setDiningCapacity(request.getDiningCapacity());
        hall.setGeneratorAvailable(request.getGeneratorAvailable());
        hall.setLiftAvailable(request.getLiftAvailable());
        hall.setContactNumber(request.getContactNumber());
        hall.setWhatsappNumber(request.getWhatsappNumber());
        hall.setCoverImageUrl(request.getCoverImageUrl() != null ? request.getCoverImageUrl() : "");
        hall.setBridalRoomAvailable(request.getBridalRoomAvailable());
        hall.setCateringKitchenAvailable(request.getCateringKitchenAvailable());

        if (request.getPricing() != null) {
            hall.setMorningAmount(request.getPricing().getMorningPrice());
            hall.setEveningAmount(request.getPricing().getEveningPrice());
            hall.setFullDayAmount(request.getPricing().getFullDayPrice());
        } else if (request.getStartingPrice() != null) {
            hall.setFullDayAmount(request.getStartingPrice());
        }
    }

    private Halls findOwnedHall(String hallId, Authentication authentication) {
        Long userId = currentUserId(authentication);
        Long numericId = tryParseLong(hallId);

        Halls hall = numericId != null
                ? hallRepository.findById(numericId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"))
                : hallRepository.findByOwnerUserId_Id(userId)
                        .stream()
                        .filter(candidate -> slugify(candidate.getName()).equals(slugify(hallId)))
                        .findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"));

        if (hall.getOwnerUserId() == null || !hall.getOwnerUserId().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hall does not belong to this owner");
        }
        return hall;
    }

    private Halls findApprovedHall(String hallId) {
        Long numericId = tryParseLong(hallId);
        Halls hall = numericId != null
                ? hallRepository.findById(numericId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"))
                : hallRepository.findByStatus(HallStatus.APPROVED)
                        .stream()
                        .filter(candidate -> slugify(candidate.getName()).equals(slugify(hallId)))
                        .findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"));

        if (hall.getStatus() != HallStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found");
        }
        return hall;
    }

    private User currentUser(Authentication authentication) {
        Long userId = currentUserId(authentication);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session is invalid"));
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is not active");
        }
        return user;
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session is invalid", exception);
        }
    }

    private HallResponse mapToResponse(Halls hall, boolean includeMedia) {
        HallResponse response = new HallResponse();
        response.setId(hall.getId());
        response.setName(hall.getName());
        response.setDescription(hall.getDescription());
        response.setAddressLine(hall.getAddressLine());
        response.setAddress(hall.getAddressLine());
        response.setPincode(hall.getPincode());
        response.setLatitude(hall.getLatitude());
        response.setLongitude(hall.getLongitude());
        response.setCity(hall.getCity());
        response.setArea(hall.getArea());
        response.setCapacityMin(hall.getCapacityMin());
        response.setCapacityMax(hall.getCapacityMax());
        response.setCapacity(capacity(hall));
        response.setFloors(hall.getFloors());
        response.setRooms(hall.getRooms());
        response.setContactNumber(hall.getContactNumber());
        response.setWhatsappNumber(hall.getWhatsappNumber());
        response.setOwnerName(hall.getOwnerName());
        response.setAcAvailable(hall.getAcAvailable());
        response.setCarParking(hall.getCarParking());
        response.setBikeParking(hall.getBikeParking());
        response.setDiningAvailable(hall.getDiningAvailable());
        response.setDiningCapacity(hall.getDiningCapacity());
        response.setGeneratorAvailable(hall.getGeneratorAvailable());
        response.setLiftAvailable(hall.getLiftAvailable());
        response.setBridalRoomAvailable(hall.getBridalRoomAvailable());
        response.setCateringKitchenAvailable(hall.getCateringKitchenAvailable());
        response.setCoverImageUrl(hall.getCoverImageUrl());
        response.setImageUrl(hall.getCoverImageUrl());
        response.setVenueType(hall.getHallType());
        response.setRatings(rating(hall));
        response.setRating(rating(hall));
        response.setReviewCount(0);
        response.setStatus(hall.getStatus() != null ? hall.getStatus().name() : null);
        response.setListingStatus(hall.getStatus() != null ? hall.getStatus().name() : null);
        response.setRejectionReason(hall.getRejectionReason());
        response.setStartingPrice(startingPrice(hall));
        response.setPricing(pricing(hall));
        response.setAmenities(amenities(hall));
        boolean approved = hall.getStatus() == HallStatus.APPROVED;
        response.setVerified(approved);
        response.setIsVerified(approved);
        response.setAvailableThisMonth(true);
        response.setApprovedAt(hall.getApprovedAt());
        if (hall.getApprovedBy() != null) {
            response.setApprovedBy(hall.getApprovedBy().getId());
        }

        if (includeMedia) {
            List<String> galleryUrls = galleryUrls(hall);
            response.setGalleryUrls(galleryUrls);
            if (!galleryUrls.isEmpty()) {
                response.setImageUrl(galleryUrls.get(0));
                if (response.getCoverImageUrl() == null || response.getCoverImageUrl().isBlank()) {
                    response.setCoverImageUrl(galleryUrls.get(0));
                }
            }
        }

        return response;
    }

    private Pricing pricing(Halls hall) {
        Pricing pricing = new Pricing();
        pricing.setMorningPrice(hall.getMorningAmount());
        pricing.setEveningPrice(hall.getEveningAmount());
        pricing.setFullDayPrice(hall.getFullDayAmount());
        return pricing;
    }

    private List<String> galleryUrls(Halls hall) {
        List<String> urls = hallMediaRepository.findByHallId_Id(hall.getId())
                .stream()
                .sorted(Comparator
                        .comparing((HallMedia media) -> !Boolean.TRUE.equals(media.getIsPrimary()))
                        .thenComparing(media -> media.getSortOrder() != null ? media.getSortOrder() : 0)
                        .thenComparing(HallMedia::getId))
                .map(HallMedia::getUrl)
                .filter(url -> url != null && !url.isBlank())
                .toList();

        if (!urls.isEmpty()) {
            return urls;
        }
        if (hall.getCoverImageUrl() != null && !hall.getCoverImageUrl().isBlank()) {
            return List.of(hall.getCoverImageUrl());
        }
        return List.of();
    }

    private List<String> amenities(Halls hall) {
        List<String> amenities = new ArrayList<>();
        addAmenity(amenities, hall.getAcAvailable(), "Air conditioned");
        addAmenity(amenities, hall.getCarParking(), "Car parking");
        addAmenity(amenities, hall.getBikeParking(), "Bike parking");
        addAmenity(amenities, hall.getDiningAvailable(), "Dining hall");
        addAmenity(amenities, hall.getGeneratorAvailable(), "Generator");
        addAmenity(amenities, hall.getLiftAvailable(), "Lift");
        addAmenity(amenities, hall.getBridalRoomAvailable(), "Bridal room");
        addAmenity(amenities, hall.getCateringKitchenAvailable(), "Catering kitchen");
        return amenities;
    }

    private void addAmenity(List<String> amenities, Boolean enabled, String label) {
        if (Boolean.TRUE.equals(enabled)) {
            amenities.add(label);
        }
    }

    private boolean matchesText(Halls hall, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String haystack = normalize("%s %s %s %s".formatted(
                hall.getName(),
                hall.getCity(),
                hall.getArea(),
                hall.getDescription()));
        return haystack.contains(normalize(q));
    }

    private boolean matchesEquals(String value, String filter) {
        return filter == null || filter.isBlank() || normalize(value).equals(normalize(filter));
    }

    private boolean matchesVenueType(Halls hall, String venueType) {
        return venueType == null || venueType.isBlank()
                || normalizeType(hall.getHallType()).equals(normalizeType(venueType));
    }

    private Comparator<Halls> publicSort(String sort) {
        String normalized = sort == null ? "RELEVANCE" : sort.trim().toUpperCase(Locale.ROOT);
        if ("RATING_DESC".equals(normalized)) {
            return Comparator.comparing(this::rating).reversed();
        }
        if ("PRICE_ASC".equals(normalized)) {
            return Comparator.comparing(hall -> startingPrice(hall) != null ? startingPrice(hall) : BigDecimal.ZERO);
        }
        if ("CAPACITY_DESC".equals(normalized)) {
            return Comparator.comparingInt(this::capacity).reversed();
        }
        return Comparator
                .comparing((Halls hall) -> Boolean.TRUE.equals(hall.getRatings() != null && hall.getRatings() > 0))
                .reversed()
                .thenComparing(this::rating, Comparator.reverseOrder())
                .thenComparing(Halls::getId);
    }

    private int capacity(Halls hall) {
        if (hall.getCapacityMax() != null) {
            return hall.getCapacityMax();
        }
        if (hall.getCapacityMin() != null) {
            return hall.getCapacityMin();
        }
        return 0;
    }

    private BigDecimal startingPrice(Halls hall) {
        BigDecimal price = minPositive(hall.getMorningAmount(), hall.getEveningAmount());
        return minPositive(price, hall.getFullDayAmount());
    }

    private BigDecimal minPositive(BigDecimal first, BigDecimal second) {
        if (first == null || BigDecimal.ZERO.compareTo(first) >= 0) {
            return second;
        }
        if (second == null || BigDecimal.ZERO.compareTo(second) >= 0) {
            return first;
        }
        return first.min(second);
    }

    private Double rating(Halls hall) {
        return hall.getRatings() != null ? hall.getRatings() : 0.0;
    }

    private Long tryParseLong(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String slugify(String value) {
        return normalize(value).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String normalizeType(String value) {
        return normalize(value).replaceAll("[\\s-]+", "_");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }
}
