package com.staminal.venue.vendorbookings;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.enums.UserRole;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendorbookings.dto.VendorServiceBookingResponse;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class VendorServiceBookingService {

    private final VendorServiceBookingRepository bookingRepository;
    private final VendorBookingPaymentRepository paymentRepository;
    private final VendorBookingTimelineRepository timelineRepository;
    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;

    public List<VendorServiceBookingResponse> getCustomerBookings(Authentication authentication) {
        User customer = currentUser(authentication, UserRole.CUSTOMER);
        return bookingRepository.findByCustomer_IdOrderByEventDateDesc(customer.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<VendorServiceBookingResponse> getVendorBookings(Authentication authentication) {
        User user = currentUser(authentication, UserRole.VENDOR);
        Vendors vendor = vendorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor profile not found"));
        return bookingRepository.findByVendor_IdOrderByEventDateDesc(vendor.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public VendorServiceBookingResponse toResponse(VendorServiceBooking booking) {
        Vendors vendor = booking.getVendor();
        User customer = booking.getCustomer();
        return new VendorServiceBookingResponse(
                formatBookingId(booking.getId()),
                booking.getQuote().getId(),
                booking.getLead().getId(),
                booking.getRequirement() == null ? null : booking.getRequirement().getId(),
                String.valueOf(vendor.getId()),
                vendorName(vendor),
                String.valueOf(customer.getId()),
                customer.getFullName(),
                customer.getPhone(),
                customer.getEmail(),
                booking.getService(),
                booking.getPackageName(),
                booking.getEventType(),
                booking.getEventDate(),
                booking.getLocation(),
                booking.getAmount(),
                booking.getStatus(),
                booking.getPaymentStatus(),
                booking.getAdvanceAmount(),
                booking.getBalanceAmount(),
                booking.getAdvanceDueDate(),
                booking.getStartedAt(),
                booking.getCompletedAt(),
                booking.getCancelledAt(),
                booking.getCancelledBy(),
                booking.getCancellationReason(),
                booking.getRefundableAmount(),
                paymentRepository.findByBooking_IdOrderByCreatedAtDesc(booking.getId())
                        .stream()
                        .map(this::toPaymentResponse)
                        .toList(),
                timelineRepository.findByBooking_IdOrderByCreatedAtDesc(booking.getId())
                        .stream()
                        .map(this::toTimelineResponse)
                        .toList(),
                booking.getStatus() == com.staminal.venue.enums.VendorServiceBookingStatus.COMPLETED,
                booking.getConfirmedAt(),
                booking.getCreatedAt(),
                booking.getUpdatedAt());
    }

    @Transactional
    public void recordCreationTimeline(VendorServiceBooking booking, User customer) {
        VendorBookingTimeline item = new VendorBookingTimeline();
        item.setBooking(booking);
        item.setEventType("BOOKING_CREATED");
        item.setToStatus(booking.getStatus().name());
        item.setActor(customer);
        item.setActorRole(UserRole.CUSTOMER.name());
        item.setMessage("Quotation accepted and booking confirmed");
        timelineRepository.save(item);
    }

    public com.staminal.venue.vendorbookings.dto.VendorBookingPaymentResponse toPaymentResponse(
            VendorBookingPayment payment) {
        return new com.staminal.venue.vendorbookings.dto.VendorBookingPaymentResponse(
                "VPAY-" + String.format("%06d", payment.getId()),
                payment.getPaymentType(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProvider(),
                payment.getProviderOrderId(),
                payment.getProviderPaymentId(),
                payment.getReceiptNumber(),
                payment.getPaidAt(),
                payment.getRefundStatus(),
                payment.getRefundAmount(),
                payment.getRefundedAt(),
                payment.getCreatedAt());
    }

    private com.staminal.venue.vendorbookings.dto.VendorBookingTimelineResponse toTimelineResponse(
            VendorBookingTimeline item) {
        return new com.staminal.venue.vendorbookings.dto.VendorBookingTimelineResponse(
                "VBT-" + String.format("%06d", item.getId()),
                item.getEventType(),
                item.getFromStatus(),
                item.getToStatus(),
                item.getActorRole(),
                item.getMessage(),
                item.getCreatedAt());
    }

    private User currentUser(Authentication authentication, UserRole role) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String authority = "ROLE_" + role.name();
        boolean hasRole = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
        if (!hasRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, role.name() + " role is required");
        }

        Long userId;
        try {
            userId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user session");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is not active");
        }
        return user;
    }

    private String formatBookingId(Long id) {
        return "VBOOK-" + String.format("%06d", id);
    }

    private String vendorName(Vendors vendor) {
        if (vendor.getBusinessName() != null && !vendor.getBusinessName().isBlank()) {
            return vendor.getBusinessName().trim();
        }
        return vendor.getVendorName() == null ? "Vendor" : vendor.getVendorName().trim();
    }
}
