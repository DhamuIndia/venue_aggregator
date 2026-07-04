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
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.leads.Dto.CreateVendorLeadRequest;
import com.staminal.venue.leads.Dto.UpdateVendorLeadStatusRequest;
import com.staminal.venue.leads.Dto.VendorLeadResponse;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
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
        if (lead.getVendor() != null) {
            response.setVendorId(String.valueOf(lead.getVendor().getId()));
            response.setVendorName(firstText(lead.getVendor().getBusinessName(), lead.getVendor().getVendorName(), ""));
        }
        if (lead.getCustomer() != null && lead.getCustomer().getId() != null) {
            response.setCustomerId(String.valueOf(lead.getCustomer().getId()));
        }
        response.setCustomerName(lead.getCustomerName());
        response.setCustomerPhone(lead.getCustomerPhone());
        response.setCustomerEmail(lead.getCustomerEmail());

        response.setService(lead.getService());
        response.setEventType(lead.getEventType());
        response.setEventDate(lead.getEventDate());

        response.setLocation(lead.getLocation());
        response.setBudget(lead.getBudget());

        response.setNotes(lead.getNotes());

        response.setStatus(lead.getStatus());

        response.setCreatedAt(lead.getCreatedAt());
        response.setUpdatedAt(lead.getUpdatedAt());

        return response;
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

        validateTransition(
                lead.getStatus(),
                request.getStatus());

        VendorLeadStatus oldStatus = lead.getStatus();

        lead.setStatus(request.getStatus());

        VendorLead savedLead = vendorLeadRepository.save(lead);

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
                        null));

        return mapToResponse(savedLead);
    }

    private void validateTransition(
            VendorLeadStatus current,
            VendorLeadStatus next) {

        if (current == VendorLeadStatus.BOOKED ||
                current == VendorLeadStatus.DECLINED) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Terminal status cannot be changed");
        }

        switch (current) {

            case NEW -> {

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
