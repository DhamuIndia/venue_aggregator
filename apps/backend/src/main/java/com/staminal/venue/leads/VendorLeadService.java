package com.staminal.venue.leads;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.enums.VendorLeadStatus;
import com.staminal.venue.enums.VendorServiceBookingStatus;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.leads.Dto.CreateVendorLeadRequest;
import com.staminal.venue.leads.Dto.UpdateVendorLeadStatusRequest;
import com.staminal.venue.leads.Dto.VendorLeadResponse;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.notifications.NotificationType;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendorbookings.VendorServiceBookingRepository;
import com.staminal.venue.vendorbookings.VendorServiceBooking;
import com.staminal.venue.quotes.VendorQuoteRepository;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class VendorLeadService {

    private final VendorLeadRepository vendorLeadRepository;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final VendorServiceBookingRepository vendorServiceBookingRepository;
    private final VendorQuoteRepository vendorQuoteRepository;

    private Vendors currentVendor(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }

        if (!hasRole(authentication, UserRole.VENDOR)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "VENDOR role is required");
        }

        Long userId;

        try {
            userId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid user session");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"));

        return vendorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Vendor profile not found"));
    }

    private User currentUser(
            Authentication authentication,
            UserRole role) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }

        if (!hasRole(authentication, role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    role.name() + " role is required");
        }

        Long userId;

        try {
            userId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid user session");
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"));
    }

    private boolean hasRole(
            Authentication authentication,
            UserRole role) {

        String authority = "ROLE_" + role.name();

        return authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private VendorLeadResponse mapToResponse(VendorLead lead) {

        VendorLeadResponse response = new VendorLeadResponse();

        response.setId(lead.getId());
        response.setLeadReference(lead.getPublicReference());
        if (lead.getVendor() != null) {
            response.setVendorId(String.valueOf(lead.getVendor().getId()));
            response.setVendorName(firstText(lead.getVendor().getBusinessName(), lead.getVendor().getVendorName(), ""));
        }
        if (lead.getCustomer() != null && lead.getCustomer().getId() != null) {
            response.setCustomerId(String.valueOf(lead.getCustomer().getId()));
        }
        boolean contactDetailsShared = lead.getRequirement() == null
                || lead.getRequirement().isShareContactDetails()
                || lead.isContactDetailsReleased();
        if (lead.isContactDetailsReleased() && lead.getCustomer() != null) {
            response.setCustomerName(firstText(lead.getCustomer().getFullName(), lead.getCustomerName()));
            response.setCustomerPhone(firstText(lead.getCustomer().getPhone(), lead.getCustomerPhone(), null));
            response.setCustomerEmail(firstText(lead.getCustomer().getEmail(), lead.getCustomerEmail(), null));
        } else {
            response.setCustomerName(lead.getCustomerName());
            response.setCustomerPhone(lead.getCustomerPhone());
            response.setCustomerEmail(lead.getCustomerEmail());
        }
        if (lead.getRequirement() != null) {
            response.setRequirementId(lead.getRequirement().getId());
            response.setSource("MARKETPLACE_REQUIREMENT");
            response.setContactDetailsShared(contactDetailsShared);
            response.setPreferredContactChannel(lead.getRequirement().getPreferredContactChannel());
        } else {
            response.setSource("DIRECT_ENQUIRY");
            response.setContactDetailsShared(true);
        }

        response.setService(lead.getService());
        response.setEventType(lead.getEventType());
        response.setEventDate(lead.getEventDate());

        response.setLocation(lead.getLocation());
        response.setBudget(lead.getBudget());

        response.setNotes(lead.getNotes());
        response.setDeclineReason(lead.getDeclineReason());

        response.setStatus(lead.getStatus());

        response.setCreatedAt(lead.getCreatedAt());
        response.setUpdatedAt(lead.getUpdatedAt());

        return response;
    }

    private void notifyLeadCreated(VendorLead lead) {

        Vendors vendor = lead.getVendor();

        String vendorName = vendor.getBusinessName() != null
                ? vendor.getBusinessName()
                : vendor.getVendorName();

        // Customer Notification
        notificationService.notifyUser(
                lead.getCustomer(),
                NotificationType.ENQUIRY,
                "Lead submitted",
                "Your enquiry was sent to " + vendorName + ".",
                "/customer?tab=enquiries");

        // Vendor Notification
        notificationService.notifyUser(
                vendor.getUser(),
                NotificationType.ENQUIRY,
                "New lead received",
                lead.getCustomerName() + " sent you a new enquiry.",
                "/vendor?tab=leads");
    }

    private void notifyCustomer(VendorLead lead) {

        Vendors vendor = lead.getVendor();

        String vendorName = vendor.getBusinessName() != null
                ? vendor.getBusinessName()
                : vendor.getVendorName();

        switch (lead.getStatus()) {

            case INTERESTED ->

                notificationService.notifyUser(
                        lead.getCustomer(),
                        NotificationType.ENQUIRY,
                        "Vendor is interested",
                        vendorName + " is interested in your requirement.",
                        lead.getRequirement() == null
                                ? "/customer?tab=enquiries"
                                : "/customer?tab=requirements");

            case CONTACTED ->

                notificationService.notifyUser(
                        lead.getCustomer(),
                        NotificationType.ENQUIRY,
                        "Vendor contacted you",
                        vendorName + " contacted you regarding your enquiry.",
                        "/customer?tab=enquiries");

            case QUOTE_SENT ->

                notificationService.notifyUser(
                        lead.getCustomer(),
                        NotificationType.ENQUIRY,
                        "Quote received",
                        vendorName + " sent you a quotation.",
                        "/customer?tab=enquiries");

            case BOOKED ->

                notificationService.notifyUser(
                        lead.getCustomer(),
                        NotificationType.BOOKING,
                        "Booking confirmed",
                        "Your booking with " + vendorName + " has been confirmed.",
                        "/customer?tab=bookings");

            case COMPLETED ->

                notificationService.notifyUser(
                        lead.getCustomer(),
                        NotificationType.BOOKING,
                        "Service completed",
                        "Your service with " + vendorName
                                + " has been marked as completed. You can now leave a review.",
                        "/customer?tab=reviews");

            case DECLINED ->

                notificationService.notifyUser(
                        lead.getCustomer(),
                        NotificationType.ENQUIRY,
                        "Lead declined",
                        vendorName + " declined your enquiry. Reason: " + lead.getDeclineReason(),
                        lead.getRequirement() == null
                                ? "/customer?tab=enquiries"
                                : "/customer?tab=requirements");

            default -> {
            }
        }
    }

    public VendorLeadResponse createLead(
            CreateVendorLeadRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }

        if (!hasRole(authentication, UserRole.CUSTOMER)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "CUSTOMER role is required");
        }

        validateCreateRequest(request);

        Long userId;

        try {
            userId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid user session");
        }

        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"));

        if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "User account is not active");
        }

        Vendors vendor = findApprovedVendor(request.getVendorId());

        VendorLead lead = new VendorLead();

        lead.setVendor(vendor);
        lead.setCustomer(customer);

        lead.setCustomerName(customer.getFullName());
        lead.setCustomerPhone(customer.getPhone());
        lead.setCustomerEmail(customer.getEmail());

        lead.setService(request.getService());
        lead.setEventType(request.getEventType());
        lead.setEventDate(request.getEventDate());
        lead.setLocation(request.getLocation());
        lead.setBudget(request.getBudget());
        lead.setNotes(request.getNotes());
        lead.setStatus(VendorLeadStatus.NEW);

        VendorLead saved = vendorLeadRepository.save(lead);
        notifyLeadCreated(saved);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<VendorLeadResponse> getMyLeads(Authentication authentication) {

        Vendors vendor = currentVendor(authentication);

        return vendorLeadRepository
                .findByVendor_IdOrderByCreatedAtDesc(vendor.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VendorLeadResponse> getMyCustomerLeads(Authentication authentication) {

        User customer = currentUser(authentication, UserRole.CUSTOMER);

        return vendorLeadRepository
                .findByCustomer_IdOrderByCreatedAtDesc(customer.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VendorLeadResponse getLead(
            Long leadId,
            Authentication authentication) {

        Vendors vendor = currentVendor(authentication);

        VendorLead lead = vendorLeadRepository
                .findByIdAndVendor_Id(leadId, vendor.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Lead not found"));

        return mapToResponse(lead);
    }

    @Transactional(readOnly = true)
    public VendorLeadResponse getLeadByReference(
            String leadReference,
            Authentication authentication) {

        Vendors vendor = currentVendor(authentication);
        if (!LeadReference.isValid(leadReference)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Lead not found");
        }

        VendorLead lead = vendorLeadRepository
                .findByPublicReferenceAndVendor_Id(leadReference, vendor.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Lead not found"));

        return mapToResponse(lead);
    }

    public VendorLeadResponse updateStatus(
            Long leadId,
            UpdateVendorLeadStatusRequest request,
            Authentication authentication) {

        if (request == null || request.getStatus() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Status is required");
        }

        Vendors vendor = currentVendor(authentication);

        VendorLead lead = vendorLeadRepository
                .findByIdAndVendor_Id(leadId, vendor.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Lead not found"));

        if (request.getStatus() == VendorLeadStatus.DECLINED
                && (request.getReason() == null || request.getReason().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decline reason is required");
        }
        if (request.getStatus() == VendorLeadStatus.BOOKED && lead.getRequirement() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Marketplace bookings are confirmed when the customer accepts a quote");
        }

        validateTransition(
                lead.getStatus(),
                request.getStatus());

        VendorLeadStatus oldStatus = lead.getStatus();

        lead.setStatus(request.getStatus());
        if (request.getStatus() == VendorLeadStatus.DECLINED) {
            lead.setDeclineReason(request.getReason().trim());
        }

        VendorLead savedLead = vendorLeadRepository.save(lead);
        if (savedLead.getStatus() == VendorLeadStatus.BOOKED) {
            createBookingForBookedLead(savedLead);
        }
        if (savedLead.getStatus() == VendorLeadStatus.COMPLETED) {
            vendorServiceBookingRepository.findByLead_Id(savedLead.getId()).ifPresent(booking -> {
                booking.setStatus(VendorServiceBookingStatus.COMPLETED);
                vendorServiceBookingRepository.save(booking);
            });
        }

        notifyCustomer(savedLead);

        auditService.record(
                new AuditCommand(
                        vendor.getUser().getId(),
                        "VENDOR",
                        AuditAction.LEAD_STATUS_CHANGED,
                        "VENDOR_LEAD",
                        String.valueOf(savedLead.getId()),
                        "Lead status changed",
                        Map.of("status", oldStatus.name()),
                        Map.of("status", savedLead.getStatus().name()),
                        request.getStatus() == VendorLeadStatus.DECLINED
                                ? Map.of("reason", savedLead.getDeclineReason())
                                : null));

        return mapToResponse(savedLead);
    }

    public List<VendorLeadResponse> getAdminLeads(Authentication authentication) {
        // currentUser(authentication, UserRole.ADMIN);
        if (!hasRole(authentication, UserRole.ADMIN)
                && !hasRole(authentication, UserRole.SUPER_ADMIN)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "ADMIN or SUPER_ADMIN role is required");
        }
        return vendorLeadRepository.findAll().stream()
                .sorted((first, second) -> second.getCreatedAt().compareTo(first.getCreatedAt()))
                .map(this::mapToResponse)
                .toList();
    }

    private void createBookingForBookedLead(VendorLead lead) {
        if (vendorServiceBookingRepository.findByLead_Id(lead.getId()).isPresent())
            return;

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
            vendorServiceBookingRepository.save(booking);
        });
    }

    private void validateTransition(
            VendorLeadStatus current,
            VendorLeadStatus next) {

        if (current == VendorLeadStatus.COMPLETED
                || current == VendorLeadStatus.DECLINED
                || current == VendorLeadStatus.NOT_SELECTED) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Terminal status cannot be changed");
        }

        switch (current) {

            case NEW -> {

                if (next != VendorLeadStatus.INTERESTED &&
                        next != VendorLeadStatus.CONTACTED &&
                        next != VendorLeadStatus.QUOTE_SENT &&
                        next != VendorLeadStatus.DECLINED) {

                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Invalid status transition");
                }
            }

            case INTERESTED -> {

                if (next != VendorLeadStatus.CONTACTED &&
                        next != VendorLeadStatus.QUOTE_SENT &&
                        next != VendorLeadStatus.DECLINED) {

                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Invalid status transition");
                }
            }

            case CONTACTED -> {

                if (next != VendorLeadStatus.QUOTE_SENT &&
                        next != VendorLeadStatus.BOOKED &&
                        next != VendorLeadStatus.DECLINED) {

                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Invalid status transition");
                }
            }

            case QUOTE_SENT -> {

                if (next != VendorLeadStatus.BOOKED &&
                        next != VendorLeadStatus.DECLINED) {

                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Invalid status transition");
                }
            }

            case BOOKED -> {

                if (next != VendorLeadStatus.COMPLETED) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Invalid status transition");
                }
            }

            default -> {
            }
        }
    }

    private void validateCreateRequest(CreateVendorLeadRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lead details are required");
        }
        if (!hasText(request.getVendorId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vendor id is required");
        }
        if (!hasText(request.getService())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Service is required");
        }
        if (!hasText(request.getEventType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event type is required");
        }
        if (request.getEventDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event date is required");
        }
        if (!hasText(request.getLocation())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location is required");
        }
        if (request.getBudget() == null || request.getBudget().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget must be greater than zero");
        }
    }

    private Vendors findApprovedVendor(String vendorId) {
        Long numericId = tryParseLong(vendorId);
        if (numericId != null) {
            Vendors vendor = vendorRepository.findById(numericId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));
            if (vendor.getStatus() != VendorStatus.APPROVED) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found");
            }
            return vendor;
        }

        String requestedSlug = slugify(vendorId);
        return vendorRepository.findByStatus(VendorStatus.APPROVED)
                .stream()
                .filter(candidate -> slugify(candidate.getBusinessName()).equals(requestedSlug)
                        || slugify(candidate.getVendorName()).equals(requestedSlug))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));
    }

    private Long tryParseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String slugify(String value) {
        String normalized = Normalizer.normalize(firstText(value, ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized;
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

}
