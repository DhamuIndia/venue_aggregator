package com.staminal.venue.bookings;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.bookings.dto.BookingListResponse;
import com.staminal.venue.bookings.dto.BookingResponse;
import com.staminal.venue.bookings.dto.UpdateBookingStatusRequest;
import com.staminal.venue.enquiries.Enquiry;
import com.staminal.venue.enums.BookingStatus;
import com.staminal.venue.enums.EnquiryStatus;
import com.staminal.venue.enums.PaymentStatus;
import com.staminal.venue.enums.SlotType;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.halls.Entity.HallBlockedDate;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallBlockedDateRepository;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final HallRepository hallRepository;
    private final UserRepository userRepository;
    private final HallBlockedDateRepository hallBlockedDateRepository;

    @Transactional(readOnly = true)
    public BookingListResponse getCustomerBookings(Authentication authentication) {
        User customer = currentUser(authentication, UserRole.CUSTOMER);
        List<BookingResponse> bookings = bookingRepository.findByCustomer_IdOrderByEventDateDesc(customer.getId())
                .stream()
                .map(this::toResponse)
                .toList();
        return new BookingListResponse(bookings, bookings.size());
    }

    @Transactional(readOnly = true)
    public BookingResponse getCustomerBooking(String bookingId, Authentication authentication) {
        User customer = currentUser(authentication, UserRole.CUSTOMER);
        Booking booking = findBooking(bookingId);
        if (booking.getCustomer() == null || !customer.getId().equals(booking.getCustomer().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Booking does not belong to this customer");
        }
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public BookingListResponse getOwnerHallBookings(String hallId, Authentication authentication) {
        User owner = currentUser(authentication, UserRole.HALL_OWNER);
        Halls hall = findHallForOwner(hallId, owner);
        List<BookingResponse> bookings = bookingRepository.findByHall_IdOrderByEventDateDesc(hall.getId())
                .stream()
                .map(this::toResponse)
                .toList();
        return new BookingListResponse(bookings, bookings.size());
    }

    public BookingResponse updateOwnerBookingStatus(
            String bookingId,
            UpdateBookingStatusRequest request,
            Authentication authentication) {
        User owner = currentUser(authentication, UserRole.HALL_OWNER);
        Booking booking = findBooking(bookingId);
        if (booking.getHall() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall booking not found");
        }
        assertOwnerCanAccess(owner, booking.getHall());

        BookingStatus currentStatus = normalizeStatus(booking.getStatus());
        BookingStatus nextStatus = request.status();
        assertValidTransition(currentStatus, nextStatus);

        if (currentStatus != nextStatus) {
            applyStatus(booking, nextStatus);
        }

        return toResponse(bookingRepository.save(booking));
    }

    private void applyStatus(Booking booking, BookingStatus nextStatus) {
        Instant now = Instant.now();
        if (nextStatus == BookingStatus.CONFIRMED) {
            ensureSlotStillAvailable(booking);
            booking.setConfirmedAt(booking.getConfirmedAt() != null ? booking.getConfirmedAt() : now);
            booking.setPaymentStatus(PaymentStatus.ADVANCE_PENDING);
            syncEnquiryStatus(booking, EnquiryStatus.CONFIRMED);
        }

        if (nextStatus == BookingStatus.CANCELLED) {
            booking.setCancelledAt(now);
            if (booking.getPaymentStatus() == PaymentStatus.ADVANCE_PAID) {
                booking.setPaymentStatus(PaymentStatus.REFUNDED);
            } else {
                booking.setPaymentStatus(PaymentStatus.NOT_STARTED);
            }
        }

        if (nextStatus == BookingStatus.COMPLETED) {
            booking.setCompletedAt(now);
            syncEnquiryStatus(booking, EnquiryStatus.COMPLETED);
        }

        booking.setStatus(nextStatus);
    }

    private void ensureSlotStillAvailable(Booking booking) {

        boolean alreadyBooked = bookingRepository.existsByHall_IdAndEventDateAndSlotTypeAndStatusAndIdNot(
                booking.getHall().getId(),
                booking.getEventDate(),
                booking.getSlotType(),
                BookingStatus.CONFIRMED,
                booking.getId());

        if (alreadyBooked) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This hall slot is already booked");
        }

        List<HallBlockedDate> blockedDates = hallBlockedDateRepository.findByHallId_Id(
                booking.getHall().getId());

        for (HallBlockedDate blocked : blockedDates) {

            if (!blocked.getEventDate().equals(booking.getEventDate())) {
                continue;
            }

            SlotType blockedSlot = blocked.getSlotType();
            SlotType bookingSlot = booking.getSlotType();

            if (blockedSlot == SlotType.FULL_DAY
                    || bookingSlot == SlotType.FULL_DAY
                    || blockedSlot == bookingSlot) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This hall slot is blocked by the owner");
            }
        }
    }

    private void syncEnquiryStatus(Booking booking, EnquiryStatus status) {
        Enquiry enquiry = booking.getEnquiry();
        if (enquiry != null) {
            enquiry.setStatus(status);
        }
    }

    private void assertValidTransition(BookingStatus currentStatus, BookingStatus nextStatus) {
        if (currentStatus == nextStatus) {
            return;
        }

        if (currentStatus == BookingStatus.REQUESTED
                && (nextStatus == BookingStatus.CONFIRMED || nextStatus == BookingStatus.CANCELLED)) {
            return;
        }

        if (currentStatus == BookingStatus.CONFIRMED
                && (nextStatus == BookingStatus.COMPLETED || nextStatus == BookingStatus.CANCELLED)) {
            return;
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Invalid booking status transition from " + currentStatus + " to " + nextStatus);
    }

    private BookingStatus normalizeStatus(BookingStatus status) {
        return status != null ? status : BookingStatus.REQUESTED;
    }

    private Booking findBooking(String bookingId) {
        return bookingRepository.findById(parseBookingId(bookingId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private Halls findHallForOwner(String hallId, User owner) {
        Halls hall = findHallByIdentifier(hallId);
        assertOwnerCanAccess(owner, hall);
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

    private BookingResponse toResponse(Booking booking) {
        Enquiry enquiry = booking.getEnquiry();
        Halls hall = booking.getHall();
        User customer = booking.getCustomer();
        String bookingId = formatBookingId(booking.getId());

        return new BookingResponse(
                bookingId,
                bookingId,
                enquiry != null ? formatEnquiryId(enquiry.getId()) : null,
                formatHallId(hall),
                hall != null ? hall.getName() : null,
                customer != null && customer.getId() != null ? String.valueOf(customer.getId()) : null,
                booking.getCustomerName(),
                booking.getEventDate(),
                enquiry != null && enquiry.getEventType() != null ? enquiry.getEventType() : "Event",
                enquiry != null && enquiry.getGuestCount() != null ? enquiry.getGuestCount() : 0,
                booking.getSlotType(),
                normalizeStatus(booking.getStatus()),
                booking.getAmount() != null ? booking.getAmount() : hall != null ? startingPrice(hall) : null,
                booking.getPaymentStatus() != null ? booking.getPaymentStatus() : PaymentStatus.NOT_STARTED,
                enquiry != null ? enquiry.getMessage() : null,
                booking.getConfirmedAt() != null ? booking.getConfirmedAt() : booking.getCreatedAt(),
                booking.getUpdatedAt() != null ? booking.getUpdatedAt() : booking.getCreatedAt());
    }

    private String formatHallId(Halls hall) {
        if (hall == null) {
            return null;
        }
        return String.valueOf(hall.getId());
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

    private long parseBookingId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.toUpperCase().startsWith("BOOK-")) {
            normalized = normalized.substring(5);
        }
        return parseNumericId(normalized, "booking");
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

    private String formatBookingId(Long id) {
        if (id == null) {
            return null;
        }
        return "BOOK-" + String.format("%06d", id);
    }

    private String formatEnquiryId(Long id) {
        if (id == null) {
            return null;
        }
        return "ENQ-" + String.format("%06d", id);
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
}
