package com.staminal.venue.enquiries;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
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
import com.staminal.venue.enquiries.dto.UpdateEnquiryStatusRequest;
import com.staminal.venue.enums.EnquiryStatus;
import com.staminal.venue.enums.SlotType;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Hall.VendorHallDetails;
import com.staminal.venue.vendors.Hall.VendorHallRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class EnquiryService {

    private static final Set<SlotType> SUPPORTED_SLOTS = EnumSet.of(
            SlotType.MORNING,
            SlotType.EVENING,
            SlotType.FULL_DAY);

    private final EnquiryRepository enquiryRepository;
    private final VendorHallRepository vendorHallRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    public EnquiryResponse createHallEnquiry(CreateEnquiryRequest request, Authentication authentication) {
        User customer = currentUser(authentication, UserRole.CUSTOMER);
        assertSupportedSlot(request.slot());

        VendorHallDetails hall = findHallByIdentifier(request.hallId());

        Enquiry enquiry = new Enquiry();
        enquiry.setHall(hall);
        enquiry.setVendor(hall.getVendor());
        enquiry.setCustomer(customer);
        enquiry.setCustomerName(customer.getFullName());
        enquiry.setCustomerPhone(customer.getPhone());
        enquiry.setCustomerEmail(customer.getEmail());
        enquiry.setEventDate(request.eventDate());
        enquiry.setEventType(request.eventType().trim());
        enquiry.setGuestCount(request.guestCount());
        enquiry.setSlotType(request.slot());
        enquiry.setMessage(trimToNull(request.notes()));
        enquiry.setStatus(EnquiryStatus.PENDING_OWNER_RESPONSE);

        return toResponse(enquiryRepository.save(enquiry));
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
        VendorHallDetails hall = findHallForOwner(hallId, owner);
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

        if (enquiry.getStatus() != nextStatus) {
            enquiry.setStatus(nextStatus);
            enquiry.setOwnerResponseMessage(trimToNull(request.message()));
            enquiry.setRespondedAt(Instant.now());
            syncBookingForStatus(enquiry, nextStatus);
        }

        return toResponse(enquiryRepository.save(enquiry));
    }

    private void syncBookingForStatus(Enquiry enquiry, EnquiryStatus nextStatus) {
        if (nextStatus == EnquiryStatus.CONFIRMED) {
            Booking booking = bookingRepository.findByEnquiry_Id(enquiry.getId())
                    .orElseGet(() -> createBooking(enquiry));
            booking.setStatus(Booking.STATUS_CONFIRMED);
            bookingRepository.save(booking);
            return;
        }

        if (nextStatus == EnquiryStatus.COMPLETED) {
            Booking booking = bookingRepository.findByEnquiry_Id(enquiry.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Confirmed booking is required before completion"));
            booking.setStatus(Booking.STATUS_COMPLETED);
            bookingRepository.save(booking);
        }
    }

    private Booking createBooking(Enquiry enquiry) {
        boolean alreadyBooked = bookingRepository.existsByHall_IdAndEventDateAndSlotTypeAndStatus(
                enquiry.getHall().getId(),
                enquiry.getEventDate(),
                enquiry.getSlotType(),
                Booking.STATUS_CONFIRMED);

        if (alreadyBooked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This hall slot is already booked");
        }

        Booking booking = new Booking();
        booking.setEnquiry(enquiry);
        booking.setHall(enquiry.getHall());
        booking.setCustomer(enquiry.getCustomer());
        booking.setEventDate(enquiry.getEventDate());
        booking.setSlotType(enquiry.getSlotType());
        booking.setStatus(Booking.STATUS_CONFIRMED);
        booking.setCustomerName(enquiry.getCustomerName());
        booking.setCustomerPhone(enquiry.getCustomerPhone());
        booking.setCustomerEmail(enquiry.getCustomerEmail());
        return booking;
    }

    private VendorHallDetails findHallForOwner(String hallId, User owner) {
        VendorHallDetails hall = findHallByIdentifier(hallId);
        assertOwnerCanAccess(owner, hall);
        return hall;
    }

    private VendorHallDetails findHallByIdentifier(String hallId) {
        Long numericId = tryParseLong(hallId);
        if (numericId != null) {
            return vendorHallRepository.findById(numericId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"));
        }

        String slug = slugify(hallId);
        return vendorHallRepository.findAll()
                .stream()
                .filter(hall -> slug.equals(slugify(hallDisplayName(hall))))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"));
    }

    private void assertOwnerCanAccess(User owner, VendorHallDetails hall) {
        Vendors vendor = hall.getVendor();
        if (vendor == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hall owner is not configured");
        }

        String ownerEmail = trimToNull(owner.getEmail());
        String ownerPhone = normalizePhone(owner.getPhone());

        boolean emailMatches = ownerEmail != null
                && vendor.getEmail() != null
                && ownerEmail.equalsIgnoreCase(vendor.getEmail().trim());

        boolean phoneMatches = ownerPhone != null
                && (ownerPhone.equals(normalizePhone(vendor.getContactNumber()))
                        || ownerPhone.equals(normalizePhone(vendor.getWhatsAppNumber())));

        if (!emailMatches && !phoneMatches) {
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
        return enquiryRepository.findById(parseEnquiryId(enquiryId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enquiry not found"));
    }

    private long parseEnquiryId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.toUpperCase().startsWith("ENQ-")) {
            normalized = normalized.substring(4);
        }
        return parseNumericId(normalized, "enquiry");
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

    private String hallDisplayName(VendorHallDetails hall) {
        Vendors vendor = hall.getVendor();
        return vendor != null ? vendor.getBusinessName() : null;
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

    private EnquiryResponse toResponse(Enquiry enquiry) {
        VendorHallDetails hall = enquiry.getHall();
        Vendors vendor = hall != null ? hall.getVendor() : enquiry.getVendor();
        User customer = enquiry.getCustomer();

        return new EnquiryResponse(
                formatEnquiryId(enquiry.getId()),
                hall != null && hall.getId() != null ? String.valueOf(hall.getId()) : null,
                vendor != null ? vendor.getBusinessName() : null,
                customer != null && customer.getId() != null ? String.valueOf(customer.getId()) : null,
                enquiry.getCustomerName(),
                enquiry.getCustomerPhone(),
                enquiry.getCustomerEmail(),
                enquiry.getEventDate(),
                enquiry.getEventType(),
                enquiry.getGuestCount(),
                enquiry.getSlotType(),
                enquiry.getMessage(),
                enquiry.getStatus(),
                enquiry.getCreatedAt(),
                enquiry.getUpdatedAt(),
                enquiry.getOwnerResponseMessage(),
                enquiry.getVersion());
    }

    private String formatEnquiryId(Long id) {
        if (id == null) {
            return null;
        }
        return "ENQ-" + String.format("%06d", id);
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
