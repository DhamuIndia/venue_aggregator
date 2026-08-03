package com.staminal.venue.vendorbookings;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.enums.UserRole;
import com.staminal.venue.enums.VendorLeadStatus;
import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.leads.VendorLeadRepository;
import com.staminal.venue.quotes.VendorQuoteRepository;
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
    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final VendorLeadRepository vendorLeadRepository;
    private final VendorQuoteRepository vendorQuoteRepository;

    @Transactional
    public List<VendorServiceBookingResponse> getCustomerBookings(Authentication authentication) {
        User customer = currentUser(authentication, UserRole.CUSTOMER);
        vendorLeadRepository.findByCustomer_IdOrderByCreatedAtDesc(customer.getId()).stream()
                .filter(lead -> lead.getStatus() == VendorLeadStatus.BOOKED)
                .forEach(this::createMissingBooking);
        return bookingRepository.findByCustomer_IdOrderByEventDateDesc(customer.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void createMissingBooking(VendorLead lead) {
        if (bookingRepository.findByLead_Id(lead.getId()).isPresent()) return;
        vendorQuoteRepository.findByLead_Id(lead.getId()).ifPresent(quote -> {
            VendorServiceBooking booking = new VendorServiceBooking();
            booking.setQuote(quote);
            booking.setLead(lead);
            booking.setRequirement(lead.getRequirement());
            booking.setVendor(lead.getVendor());
            booking.setCustomer(lead.getCustomer());
            booking.setService(lead.getService());
            booking.setPackageName(quote.getPackageName());
            booking.setEventType(lead.getEventType());
            booking.setEventDate(lead.getEventDate());
            booking.setLocation(lead.getLocation());
            booking.setAmount(quote.getAmount());
            bookingRepository.save(booking);
        });
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
                booking.getConfirmedAt(),
                booking.getCreatedAt(),
                booking.getUpdatedAt());
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
