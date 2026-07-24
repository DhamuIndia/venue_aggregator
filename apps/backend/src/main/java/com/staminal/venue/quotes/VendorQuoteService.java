package com.staminal.venue.quotes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.staminal.venue.enums.VendorQuoteStatus;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.leads.VendorLeadRepository;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.notifications.NotificationType;
import com.staminal.venue.quotes.dto.UpsertVendorQuoteRequest;
import com.staminal.venue.quotes.dto.UpdateQuoteShortlistRequest;
import com.staminal.venue.quotes.dto.VendorQuoteResponse;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class VendorQuoteService {

    private final VendorQuoteRepository vendorQuoteRepository;
    private final VendorLeadRepository vendorLeadRepository;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public VendorQuoteResponse saveQuote(
            Long leadId,
            UpsertVendorQuoteRequest request,
            Authentication authentication) {
        Vendors vendor = currentVendor(authentication);
        VendorLead lead = vendorLeadRepository.findByIdAndVendor_Id(leadId, vendor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
        validateLeadCanReceiveQuote(lead);
        validateValidityDate(request.validUntil(), lead.getEventDate());

        VendorQuote quote = vendorQuoteRepository.findByLead_Id(leadId).orElse(null);
        boolean created = quote == null;
        boolean shortlistCleared = !created && quote.isShortlisted();
        Map<String, Object> oldValues = created ? null : quoteValues(quote);
        if (created) {
            quote = new VendorQuote();
            quote.setLead(lead);
            quote.setVendor(vendor);
        } else {
            quote.setShortlisted(false);
            quote.setShortlistedAt(null);
        }

        quote.setAmount(request.amount());
        quote.setPackageName(request.packageName().trim());
        quote.setServiceDescription(request.serviceDescription().trim());
        quote.setInclusions(normalizeInclusions(request.inclusions()));
        quote.setAdditionalCharges(defaultZero(request.additionalCharges()));
        quote.setAdditionalChargesDescription(trimToNull(request.additionalChargesDescription()));
        quote.setNotes(trimToNull(request.notes()));
        quote.setValidUntil(request.validUntil());
        quote.setStatus(VendorQuoteStatus.SENT);

        VendorQuote saved = vendorQuoteRepository.save(quote);
        VendorLeadStatus previousLeadStatus = lead.getStatus();
        lead.setStatus(VendorLeadStatus.QUOTE_SENT);
        lead.setDeclineReason(null);
        vendorLeadRepository.save(lead);

        notifyCustomer(saved, created);
        auditService.record(new AuditCommand(
                vendor.getUser().getId(),
                UserRole.VENDOR.name(),
                created ? AuditAction.QUOTE_CREATED : AuditAction.QUOTE_UPDATED,
                "VENDOR_QUOTE",
                String.valueOf(saved.getId()),
                created ? "Vendor quote sent" : "Vendor quote updated",
                oldValues,
                quoteValues(saved),
                Map.of(
                        "leadId", lead.getId(),
                        "previousLeadStatus", previousLeadStatus.name(),
                        "leadStatus", lead.getStatus().name(),
                        "shortlistCleared", shortlistCleared)));

        return toResponse(saved, false);
    }

    @Transactional(readOnly = true)
    public List<VendorQuoteResponse> getMyVendorQuotes(Authentication authentication) {
        Vendors vendor = currentVendor(authentication);
        return vendorQuoteRepository.findByVendor_IdOrderByUpdatedAtDesc(vendor.getId())
                .stream()
                .map(quote -> toResponse(quote, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VendorQuoteResponse> getMyCustomerQuotes(Authentication authentication) {
        User customer = currentUser(authentication, UserRole.CUSTOMER);
        return vendorQuoteRepository.findByLead_Customer_IdOrderByUpdatedAtDesc(customer.getId())
                .stream()
                .map(quote -> toResponse(quote, true))
                .toList();
    }

    public VendorQuoteResponse updateShortlist(
            Long quoteId,
            UpdateQuoteShortlistRequest request,
            Authentication authentication) {
        User customer = currentUser(authentication, UserRole.CUSTOMER);
        VendorQuote quote = vendorQuoteRepository.findByIdAndLead_Customer_Id(quoteId, customer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found"));
        boolean shortlisted = request.shortlisted();
        if (shortlisted) {
            validateQuoteCanBeShortlisted(quote);
        }
        if (quote.isShortlisted() == shortlisted) {
            return toResponse(quote, true);
        }

        boolean previousValue = quote.isShortlisted();
        quote.setShortlisted(shortlisted);
        quote.setShortlistedAt(shortlisted ? Instant.now() : null);
        VendorQuote saved = vendorQuoteRepository.save(quote);

        auditService.record(new AuditCommand(
                customer.getId(),
                UserRole.CUSTOMER.name(),
                shortlisted ? AuditAction.QUOTE_SHORTLISTED : AuditAction.QUOTE_UNSHORTLISTED,
                "VENDOR_QUOTE",
                String.valueOf(saved.getId()),
                shortlisted ? "Customer shortlisted vendor quote" : "Customer removed vendor quote from shortlist",
                Map.of("shortlisted", previousValue),
                Map.of("shortlisted", shortlisted),
                Map.of(
                        "leadId", saved.getLead().getId(),
                        "vendorId", saved.getVendor().getId())));

        return toResponse(saved, true);
    }

    private Vendors currentVendor(Authentication authentication) {
        User user = currentUser(authentication, UserRole.VENDOR);
        Vendors vendor = vendorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor profile not found"));
        if (vendor.getStatus() != VendorStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Approved vendor profile is required");
        }
        return vendor;
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

    private void validateLeadCanReceiveQuote(VendorLead lead) {
        if (lead.getStatus() == VendorLeadStatus.DECLINED
                || lead.getStatus() == VendorLeadStatus.BOOKED
                || lead.getStatus() == VendorLeadStatus.NOT_SELECTED
                || lead.getStatus() == VendorLeadStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This lead cannot receive a quote");
        }
        if (lead.getRequirement() != null
                && lead.getRequirement().getStatus() != null
                && lead.getRequirement().getStatus() != com.staminal.venue.enums.CustomerRequirementStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This requirement is closed");
        }
        if (lead.getEventDate() != null && lead.getEventDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The event date has passed");
        }
    }

    private void validateValidityDate(LocalDate validUntil, LocalDate eventDate) {
        if (eventDate != null && validUntil.isAfter(eventDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quote validity date cannot be after the event date");
        }
    }

    private void validateQuoteCanBeShortlisted(VendorQuote quote) {
        if (quote.getStatus() != VendorQuoteStatus.SENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only active quotes can be shortlisted");
        }
        if (quote.getValidUntil().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expired quotes cannot be shortlisted");
        }
    }

    private List<String> normalizeInclusions(List<String> inclusions) {
        return inclusions.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private void notifyCustomer(VendorQuote quote, boolean created) {
        VendorLead lead = quote.getLead();
        String vendorName = vendorName(quote.getVendor());
        String actionHref = lead.getRequirement() == null
                ? "/customer?tab=enquiries"
                : "/customer?tab=requirements";
        notificationService.notifyUser(
                lead.getCustomer(),
                NotificationType.ENQUIRY,
                created ? "New vendor quotation" : "Vendor quotation updated",
                vendorName + (created ? " sent" : " updated")
                        + " a quote for " + lead.getService() + ".",
                actionHref);
    }

    private VendorQuoteResponse toResponse(VendorQuote quote, boolean includeCustomerDecision) {
        VendorLead lead = quote.getLead();
        BigDecimal additionalCharges = defaultZero(quote.getAdditionalCharges());
        return new VendorQuoteResponse(
                quote.getId(),
                lead.getId(),
                lead.getRequirement() == null ? null : lead.getRequirement().getId(),
                String.valueOf(quote.getVendor().getId()),
                vendorName(quote.getVendor()),
                lead.getService(),
                quote.getAmount(),
                quote.getPackageName(),
                quote.getServiceDescription(),
                List.copyOf(quote.getInclusions()),
                additionalCharges,
                quote.getAdditionalChargesDescription(),
                quote.getAmount().add(additionalCharges),
                quote.getNotes(),
                quote.getValidUntil(),
                quote.getStatus(),
                includeCustomerDecision && quote.isShortlisted(),
                includeCustomerDecision ? quote.getShortlistedAt() : null,
                quote.getCreatedAt(),
                quote.getUpdatedAt());
    }

    VendorQuoteResponse toCustomerResponse(VendorQuote quote) {
        return toResponse(quote, true);
    }

    private Map<String, Object> quoteValues(VendorQuote quote) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("amount", quote.getAmount());
        values.put("packageName", quote.getPackageName());
        values.put("inclusionCount", quote.getInclusions().size());
        values.put("additionalCharges", defaultZero(quote.getAdditionalCharges()));
        values.put("validUntil", quote.getValidUntil().toString());
        values.put("status", quote.getStatus().name());
        return values;
    }

    private String vendorName(Vendors vendor) {
        if (vendor.getBusinessName() != null && !vendor.getBusinessName().isBlank()) {
            return vendor.getBusinessName().trim();
        }
        return vendor.getVendorName() == null ? "Vendor" : vendor.getVendorName().trim();
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
