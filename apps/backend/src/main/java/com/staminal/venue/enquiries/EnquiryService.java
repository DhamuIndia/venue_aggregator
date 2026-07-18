package com.staminal.venue.enquiries;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.bookings.Booking;
import com.staminal.venue.bookings.BookingRepository;
import com.staminal.venue.enquiries.dto.CreateEnquiryRequest;
import com.staminal.venue.enquiries.dto.EnquiryResponse;
import com.staminal.venue.enquiries.dto.EnquirySlotRequestDto;
import com.staminal.venue.enquiries.dto.UpdateEnquiryStatusRequest;
import com.staminal.venue.enums.EnquiryStatus;
import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.enums.PaymentStatus;
import com.staminal.venue.enums.SlotType;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.halls.Entity.HallBlockedDate;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallBlockedDateRepository;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.notifications.NotificationType;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class EnquiryService {

    private static final Set<SlotType> SUPPORTED_SLOTS = EnumSet.of(
            SlotType.MORNING,
            SlotType.AFTERNOON,
            SlotType.EVENING,
            SlotType.FULL_DAY);

    private static final List<SlotType> DAY_SLOTS = List.of(
            SlotType.MORNING,
            SlotType.AFTERNOON,
            SlotType.EVENING);

    private final EnquiryRepository enquiryRepository;
    private final HallRepository hallRepository;
    private final BookingRepository bookingRepository;
    private final HallBlockedDateRepository hallBlockedDateRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public EnquiryResponse createHallEnquiry(CreateEnquiryRequest request, Authentication authentication) {
        User customer = currentUser(authentication, UserRole.CUSTOMER);
        assertSupportedSlot(request.slot());
        List<EnquirySlotRequestDto> slotRequests = normalizeSlotRequests(request);

        Halls hall = findApprovedHallByIdentifier(request.hallId());

        Enquiry enquiry = new Enquiry();
        enquiry.setHall(hall);
        enquiry.setCustomer(customer);
        enquiry.setCustomerName(customer.getFullName());
        enquiry.setCustomerPhone(customer.getPhone());
        enquiry.setCustomerEmail(customer.getEmail());
        enquiry.setEventDate(request.eventDate());
        enquiry.setEventType(request.eventType().trim());
        enquiry.setGuestCount(request.guestCount());
        enquiry.setSlotType(request.slot());
        enquiry.setSlotRequests(slotRequests.stream()
                .map(this::toSlotRequestEntity)
                .toList());
        enquiry.setMessage(trimToNull(request.notes()));
        enquiry.setStatus(EnquiryStatus.PENDING_OWNER_RESPONSE);

        Enquiry saved = enquiryRepository.save(enquiry);
        notifyEnquiryCreated(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<EnquiryResponse> getCustomerEnquiries(Authentication authentication) {
        User customer = currentUser(authentication, UserRole.CUSTOMER);
        return enquiryRepository.findByCustomer_IdOrderByCreatedAtDesc(customer.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EnquiryResponse getCustomerEnquiry(String enquiryId, Authentication authentication) {
        User customer = currentUser(authentication, UserRole.CUSTOMER);
        Enquiry enquiry = findEnquiry(enquiryId);
        if (enquiry.getCustomer() == null || !customer.getId().equals(enquiry.getCustomer().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Enquiry does not belong to this customer");
        }
        return toResponse(enquiry);
    }

    @Transactional(readOnly = true)
    public List<EnquiryResponse> getOwnerHallEnquiries(String hallId, Authentication authentication) {
        User owner = currentUser(authentication, UserRole.HALL_OWNER);
        Halls hall = findHallForOwner(hallId, owner);
        return enquiryRepository.findByHall_IdOrderByCreatedAtDesc(hall.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public EnquiryResponse updateOwnerEnquiryStatus(
            String enquiryId,
            UpdateEnquiryStatusRequest request,
            Authentication authentication) {
        User owner = currentUser(authentication, UserRole.HALL_OWNER);
        Enquiry enquiry = findEnquiry(enquiryId);
        if (enquiry.getHall() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall enquiry not found");
        }
        assertOwnerCanAccess(owner, enquiry.getHall());

        EnquiryStatus nextStatus = request.status();
        assertValidTransition(enquiry.getStatus(), nextStatus);

        boolean statusChanged = enquiry.getStatus() != nextStatus;
        if (statusChanged) {
            enquiry.setStatus(nextStatus);
            enquiry.setOwnerResponseMessage(trimToNull(request.message()));
            enquiry.setRespondedAt(Instant.now());
            syncBookingForStatus(enquiry, nextStatus);
        }

        Enquiry saved = enquiryRepository.save(enquiry);
        if (statusChanged) {
            notifyCustomerOfOwnerResponse(saved, nextStatus);
        }

        return toResponse(saved);
    }

    private void syncBookingForStatus(Enquiry enquiry, EnquiryStatus nextStatus) {
        if (nextStatus == EnquiryStatus.CONFIRMED) {
            Booking booking = bookingRepository.findByEnquiry_Id(enquiry.getId())
                    .orElseGet(() -> createBooking(enquiry));
            booking.setStatus(Booking.STATUS_CONFIRMED);
            if (booking.getConfirmedAt() == null) {
                booking.setConfirmedAt(Instant.now());
            }
            booking.setPaymentStatus(PaymentStatus.ADVANCE_PENDING);
            bookingRepository.save(booking);
            return;
        }

        if (nextStatus == EnquiryStatus.COMPLETED) {
            Booking booking = bookingRepository.findByEnquiry_Id(enquiry.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Confirmed booking is required before completion"));
            booking.setStatus(Booking.STATUS_COMPLETED);
            booking.setCompletedAt(Instant.now());
            bookingRepository.save(booking);
        }
    }

    private Booking createBooking(Enquiry enquiry) {
        List<EnquirySlotRequestDto> requestedSlots = slotRequestsFor(enquiry);
        ensureRequestedSlotsAvailable(enquiry, requestedSlots);

        Booking booking = new Booking();
        booking.setEnquiry(enquiry);
        booking.setHall(enquiry.getHall());
        booking.setCustomer(enquiry.getCustomer());
        booking.setEventDate(requestedSlots.get(0).date());
        booking.setSlotType(enquiry.getSlotType());
        booking.setStatus(Booking.STATUS_CONFIRMED);
        booking.setAmount(startingPrice(enquiry.getHall()));
        booking.setPaymentStatus(PaymentStatus.ADVANCE_PENDING);
        booking.setConfirmedAt(Instant.now());
        booking.setCustomerName(enquiry.getCustomerName());
        booking.setCustomerPhone(enquiry.getCustomerPhone());
        booking.setCustomerEmail(enquiry.getCustomerEmail());
        return booking;
    }

    private void ensureRequestedSlotsAvailable(Enquiry enquiry, List<EnquirySlotRequestDto> requestedSlots) {
        Long hallId = enquiry.getHall().getId();

        List<Booking> confirmedBookings = bookingRepository.findByHall_IdAndStatus(
                hallId,
                Booking.STATUS_CONFIRMED);

        for (Booking booking : confirmedBookings) {
            if (booking.getEnquiry() != null && enquiry.getId() != null
                    && enquiry.getId().equals(booking.getEnquiry().getId())) {
                continue;
            }

            for (EnquirySlotRequestDto requestedSlot : requestedSlots) {
                for (EnquirySlotRequestDto bookedSlot : slotRequestsFor(booking)) {
                    if (sameDate(requestedSlot, bookedSlot)
                            && slotsConflict(requestedSlot.slot(), bookedSlot.slot())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "This hall slot is already booked");
                    }
                }
            }
        }

        List<HallBlockedDate> blockedDates = hallBlockedDateRepository.findByHallId_Id(hallId);
        for (HallBlockedDate blockedDate : blockedDates) {
            for (EnquirySlotRequestDto requestedSlot : requestedSlots) {
                if (blockedDate.getEventDate() != null
                        && blockedDate.getSlotType() != null
                        && blockedDate.getEventDate().equals(requestedSlot.date())
                        && slotsConflict(blockedDate.getSlotType(), requestedSlot.slot())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "This hall slot is blocked by the owner");
                }
            }
        }
    }

    private boolean sameDate(EnquirySlotRequestDto first, EnquirySlotRequestDto second) {
        return first.date().equals(second.date());
    }

    private boolean slotsConflict(SlotType first, SlotType second) {
        return first == SlotType.FULL_DAY
                || second == SlotType.FULL_DAY
                || first == second;
    }

    private Halls findHallForOwner(String hallId, User owner) {
        Halls hall = findHallByIdentifier(hallId);
        assertOwnerCanAccess(owner, hall);
        return hall;
    }

    private Halls findApprovedHallByIdentifier(String hallId) {
        Halls hall = findHallByIdentifier(hallId);
        if (hall.getStatus() != HallStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found");
        }
        return hall;
    }

    private Halls findHallByIdentifier(String hallId) {
        Long numericId = tryParseLong(hallId);
        if (numericId != null) {
            return hallRepository.findById(numericId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"));
        }

        String slug = slugify(hallId);
        return hallRepository.findAll()
                .stream()
                .filter(hall -> slug.equals(slugify(hallDisplayName(hall))))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"));
    }

    private void assertOwnerCanAccess(User owner, Halls hall) {
        if (hall.getOwnerUserId() == null || !owner.getId().equals(hall.getOwnerUserId().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hall does not belong to this owner");
        }
    }

    private void assertValidTransition(EnquiryStatus currentStatus, EnquiryStatus nextStatus) {
        if (currentStatus == nextStatus) {
            return;
        }

        EnquiryStatus normalizedCurrent = normalizeStatus(currentStatus);
        if (normalizedCurrent == EnquiryStatus.PENDING_OWNER_RESPONSE
                && (nextStatus == EnquiryStatus.CONFIRMED || nextStatus == EnquiryStatus.DECLINED)) {
            return;
        }

        if (normalizedCurrent == EnquiryStatus.CONFIRMED && nextStatus == EnquiryStatus.COMPLETED) {
            return;
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Invalid enquiry status transition from " + currentStatus + " to " + nextStatus);
    }

    private EnquiryStatus normalizeStatus(EnquiryStatus status) {
        if (status == EnquiryStatus.NEW || status == EnquiryStatus.CONTACTED) {
            return EnquiryStatus.PENDING_OWNER_RESPONSE;
        }
        if (status == EnquiryStatus.CLOSED) {
            return EnquiryStatus.COMPLETED;
        }
        return status;
    }

    private void assertSupportedSlot(SlotType slot) {
        if (!SUPPORTED_SLOTS.contains(slot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported enquiry slot");
        }
    }

    private List<EnquirySlotRequestDto> normalizeSlotRequests(CreateEnquiryRequest request) {
        List<EnquirySlotRequestDto> requestedSlots = request.slotRequests();
        if (requestedSlots == null || requestedSlots.isEmpty()) {
            return expandSlotRequest(request.eventDate(), request.slot(), request.eventType());
        }

        Map<String, EnquirySlotRequestDto> uniqueRequests = new LinkedHashMap<>();
        for (EnquirySlotRequestDto requestedSlot : requestedSlots) {
            if (requestedSlot == null || requestedSlot.date() == null || requestedSlot.slot() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested slots are incomplete");
            }
            if (requestedSlot.date().isBefore(request.eventDate())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Requested slot date cannot be before the event date");
            }

            String eventType = trimToNull(requestedSlot.eventType()) != null
                    ? requestedSlot.eventType()
                    : request.eventType();
            for (EnquirySlotRequestDto expandedSlot : expandSlotRequest(requestedSlot.date(), requestedSlot.slot(), eventType)) {
                uniqueRequests.put(expandedSlot.date() + "|" + expandedSlot.slot(), expandedSlot);
            }
        }

        return new ArrayList<>(uniqueRequests.values());
    }

    private List<EnquirySlotRequestDto> expandSlotRequest(LocalDate date, SlotType slot) {
        return expandSlotRequest(date, slot, null);
    }

    private List<EnquirySlotRequestDto> expandSlotRequest(LocalDate date, SlotType slot, String eventType) {
        assertSupportedSlot(slot);
        String normalizedEventType = trimToNull(eventType);
        if (slot == SlotType.FULL_DAY) {
            return DAY_SLOTS.stream()
                    .map(daySlot -> new EnquirySlotRequestDto(date, daySlot, normalizedEventType))
                    .toList();
        }
        return List.of(new EnquirySlotRequestDto(date, slot, normalizedEventType));
    }

    private EnquirySlotRequest toSlotRequestEntity(EnquirySlotRequestDto request) {
        EnquirySlotRequest slotRequest = new EnquirySlotRequest();
        slotRequest.setEventDate(request.date());
        slotRequest.setSlotType(request.slot());
        slotRequest.setEventType(trimToNull(request.eventType()));
        return slotRequest;
    }

    private List<EnquirySlotRequestDto> slotRequestsFor(Enquiry enquiry) {
        if (enquiry != null && enquiry.getSlotRequests() != null && !enquiry.getSlotRequests().isEmpty()) {
            return enquiry.getSlotRequests()
                    .stream()
                    .map(slotRequest -> new EnquirySlotRequestDto(
                            slotRequest.getEventDate(),
                            slotRequest.getSlotType(),
                            trimToNull(slotRequest.getEventType()) != null
                                    ? slotRequest.getEventType()
                                    : enquiry.getEventType()))
                    .toList();
        }

        if (enquiry == null || enquiry.getEventDate() == null || enquiry.getSlotType() == null) {
            return List.of();
        }

        return expandSlotRequest(enquiry.getEventDate(), enquiry.getSlotType());
    }

    private List<EnquirySlotRequestDto> slotRequestsFor(Booking booking) {
        if (booking == null) {
            return List.of();
        }

        List<EnquirySlotRequestDto> enquirySlots = slotRequestsFor(booking.getEnquiry());
        if (!enquirySlots.isEmpty()) {
            return enquirySlots;
        }

        if (booking.getEventDate() == null || booking.getSlotType() == null) {
            return List.of();
        }

        return expandSlotRequest(booking.getEventDate(), booking.getSlotType());
    }

    private User currentUser(Authentication authentication, UserRole requiredRole) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!hasRole(authentication, requiredRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, requiredRole + " role is required");
        }

        try {
            Long userId = Long.valueOf(authentication.getName());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session is invalid"));
            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is not active");
            }
            return user;
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session is invalid", exception);
        }
    }

    private boolean hasRole(Authentication authentication, UserRole role) {
        String authority = "ROLE_" + role.name();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private Enquiry findEnquiry(String enquiryId) {
        return enquiryRepository.findById(EnquiryIds.parse(enquiryId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found"));
    }

    private long parseNumericId(String value, String resourceName) {
        try {
            return Long.parseLong(value.trim());
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + resourceName + " id", exception);
        }
    }

    private Long tryParseLong(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String hallDisplayName(Halls hall) {
        return hall != null ? hall.getName() : null;
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

    private String slugify(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private void notifyEnquiryCreated(Enquiry enquiry) {
        Halls hall = enquiry.getHall();
        String hallName = hallName(hall);

        notificationService.notifyUser(
                enquiry.getCustomer(),
                NotificationType.ENQUIRY,
                "Enquiry submitted",
                "Your enquiry for " + hallName + " was sent to the owner.",
                "/customer?tab=enquiries");

        notificationService.notifyUser(
                hall != null ? hall.getOwnerUserId() : null,
                NotificationType.ENQUIRY,
                "New enquiry received",
                safe(enquiry.getCustomerName(), "A customer") + " enquired for " + hallName + ".",
                "/owner?tab=enquiries");
    }

    private void notifyCustomerOfOwnerResponse(Enquiry enquiry, EnquiryStatus nextStatus) {
        Halls hall = enquiry.getHall();
        String hallName = hallName(hall);

        if (nextStatus == EnquiryStatus.CONFIRMED) {
            notificationService.notifyUser(
                    enquiry.getCustomer(),
                    NotificationType.BOOKING,
                    "Booking confirmed",
                    hallName + " confirmed your " + safe(enquiry.getEventType(), "event") + " enquiry.",
                    "/customer?tab=bookings");
            return;
        }

        if (nextStatus == EnquiryStatus.DECLINED) {
            notificationService.notifyUser(
                    enquiry.getCustomer(),
                    NotificationType.ENQUIRY,
                    "Enquiry declined",
                    hallName + " declined your " + safe(enquiry.getEventType(), "event") + " enquiry.",
                    "/customer?tab=enquiries");
            return;
        }

        if (nextStatus == EnquiryStatus.COMPLETED) {
            notificationService.notifyUser(
                    enquiry.getCustomer(),
                    NotificationType.REVIEW,
                    "Review your completed service",
                    "Your completed event at " + hallName + " is ready for a verified review.",
                    "/customer?tab=reviews");
        }
    }

    private String hallName(Halls hall) {
        return hall != null && hall.getName() != null && !hall.getName().isBlank()
                ? hall.getName()
                : "the venue";
    }

    private String safe(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private EnquiryResponse toResponse(Enquiry enquiry) {
        Halls hall = enquiry.getHall();
        User customer = enquiry.getCustomer();

        return new EnquiryResponse(
                formatEnquiryId(enquiry.getId()),
                hall != null ? String.valueOf(hall.getId()) : null,
                hall != null ? hall.getName() : null,
                customer != null && customer.getId() != null ? String.valueOf(customer.getId()) : null,
                enquiry.getCustomerName(),
                enquiry.getCustomerPhone(),
                enquiry.getCustomerEmail(),
                enquiry.getEventDate(),
                enquiry.getEventType(),
                enquiry.getGuestCount(),
                enquiry.getSlotType(),
                slotRequestsFor(enquiry),
                enquiry.getMessage(),
                enquiry.getStatus(),
                enquiry.getCreatedAt(),
                enquiry.getUpdatedAt(),
                enquiry.getOwnerResponseMessage(),
                enquiry.getVersion());
    }

    private String formatEnquiryId(Long id) {
        return EnquiryIds.format(id);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizePhone(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits.isBlank() ? null : digits;
    }
}
