package com.staminal.venue.requirements;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.enums.VendorLeadStatus;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.leads.VendorLeadRepository;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.notifications.NotificationType;
import com.staminal.venue.notifications.queue.MarketplaceVendorLeadCreatedEvent;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class RequirementMatchingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequirementMatchingService.class);

    private final VendorRepository vendorRepository;
    private final VendorLeadRepository vendorLeadRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public RequirementMatchResult distribute(CustomerRequirement requirement) {
        List<Vendors> eligibleVendors = vendorRepository.findByStatus(VendorStatus.APPROVED)
                .stream()
                .filter(this::hasActiveUser)
                .filter(vendor -> matchesAnyRequestedCategory(vendor, requirement))
                .filter(vendor -> servesLocation(vendor, requirement))
                .toList();

        int createdLeadCount = 0;
        for (Vendors vendor : eligibleVendors) {
            if (vendorLeadRepository.existsByRequirement_IdAndVendor_Id(requirement.getId(), vendor.getId())) {
                continue;
            }

            VendorLead lead = vendorLeadRepository.save(toLead(requirement, vendor));
            notifyVendor(lead, requirement);
            publishLeadCreated(lead);
            createdLeadCount++;
        }

        if (createdLeadCount > 0) {
            notifyCustomer(requirement, createdLeadCount);
        } else if (eligibleVendors.isEmpty()) {
            notifyNoImmediateMatches(requirement);
        }

        auditService.record(new AuditCommand(
                requirement.getCustomer().getId(),
                UserRole.CUSTOMER.name(),
                AuditAction.REQUIREMENT_DISTRIBUTED,
                "CUSTOMER_REQUIREMENT",
                String.valueOf(requirement.getId()),
                "Marketplace requirement distributed to eligible vendors",
                null,
                Map.of(
                        "eligibleVendorCount", eligibleVendors.size(),
                        "createdLeadCount", createdLeadCount),
                null));

        return new RequirementMatchResult(eligibleVendors.size(), createdLeadCount);
    }

    private void publishLeadCreated(VendorLead lead) {
        if (lead.getId() == null) {
            LOGGER.error("Could not schedule notification queueing because the saved vendor lead has no id");
            return;
        }
        try {
            eventPublisher.publishEvent(new MarketplaceVendorLeadCreatedEvent(lead.getId()));
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Could not schedule notification queueing for vendor lead {}",
                    lead.getId(),
                    exception);
        }
    }

    @Transactional(readOnly = true)
    public long countLeads(Long requirementId) {
        if (requirementId == null) {
            return 0;
        }
        return vendorLeadRepository.countByRequirement_Id(requirementId);
    }

    private VendorLead toLead(CustomerRequirement requirement, Vendors vendor) {
        User customer = requirement.getCustomer();
        boolean shareContactDetails = requirement.isShareContactDetails();

        VendorLead lead = new VendorLead();
        lead.setVendor(vendor);
        lead.setCustomer(customer);
        lead.setRequirement(requirement);
        lead.setCustomerName(shareContactDetails ? customer.getFullName() : "VenueMart customer");
        lead.setCustomerPhone(shareContactDetails ? customer.getPhone() : null);
        lead.setCustomerEmail(shareContactDetails ? customer.getEmail() : null);
        lead.setService(matchingServiceNames(vendor, requirement));
        lead.setEventType(requirement.getEventType());
        lead.setEventDate(requirement.getEventDate());
        lead.setLocation(fullLocation(requirement));
        lead.setBudget(preferredBudget(requirement));
        lead.setNotes(requirement.getDetails());
        lead.setStatus(VendorLeadStatus.NEW);
        return lead;
    }

    private boolean hasActiveUser(Vendors vendor) {
        return vendor.getUser() != null
                && vendor.getUser().getId() != null
                && "ACTIVE".equalsIgnoreCase(vendor.getUser().getStatus());
    }

    private boolean matchesAnyRequestedCategory(Vendors vendor, CustomerRequirement requirement) {
        Set<Long> requestedCategoryIds = requirement.getServiceCategories()
                .stream()
                .map(VendorCategory::getId)
                .collect(Collectors.toSet());
        return vendor.getCategories() != null
                && vendor.getCategories()
                        .stream()
                        .map(VendorCategory::getId)
                        .anyMatch(requestedCategoryIds::contains);
    }

    private boolean servesLocation(Vendors vendor, CustomerRequirement requirement) {
        String requirementPincode = normalize(requirement.getPincode());
        String vendorPincode = normalize(vendor.getPincode());
        if (!requirementPincode.isEmpty()
                && !vendorPincode.isEmpty()
                && requirementPincode.equals(vendorPincode)) {
            return true;
        }

        String requirementCity = normalize(requirement.getCity());
        String vendorCity = normalize(vendor.getCity());
        return !requirementCity.isEmpty()
                && !vendorCity.isEmpty()
                && requirementCity.equals(vendorCity);
    }

    private String matchingServiceNames(Vendors vendor, CustomerRequirement requirement) {
        Set<Long> vendorCategoryIds = vendor.getCategories()
                .stream()
                .map(VendorCategory::getId)
                .collect(Collectors.toSet());
        return requirement.getServiceCategories()
                .stream()
                .filter(category -> vendorCategoryIds.contains(category.getId()))
                .sorted(Comparator.comparing(VendorCategory::getCategoryName, String.CASE_INSENSITIVE_ORDER))
                .map(VendorCategory::getCategoryName)
                .collect(Collectors.joining(", "));
    }

    private BigDecimal preferredBudget(CustomerRequirement requirement) {
        return requirement.getBudgetMax() != null
                ? requirement.getBudgetMax()
                : requirement.getBudgetMin();
    }

    private String fullLocation(CustomerRequirement requirement) {
        return List.of(requirement.getLocation(), nullToEmpty(requirement.getCity()))
                .stream()
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private void notifyVendor(VendorLead lead, CustomerRequirement requirement) {
        String message = "A customer needs " + lead.getService()
                + " for " + requirement.getEventType()
                + " on " + requirement.getEventDate()
                + " in " + firstText(requirement.getCity(), requirement.getLocation()) + ".";
        notificationService.notifyUser(
                lead.getVendor().getUser(),
                NotificationType.ENQUIRY,
                "New marketplace requirement",
                message,
                "/vendor?tab=leads");
    }

    private void notifyCustomer(CustomerRequirement requirement, int vendorCount) {
        String vendorWord = vendorCount == 1 ? "vendor" : "vendors";
        notificationService.notifyUser(
                requirement.getCustomer(),
                NotificationType.ENQUIRY,
                "Requirement sent to vendors",
                "Your requirement was sent to " + vendorCount + " matching " + vendorWord + ".",
                "/customer?tab=requirements");
    }

    private void notifyNoImmediateMatches(CustomerRequirement requirement) {
        notificationService.notifyUser(
                requirement.getCustomer(),
                NotificationType.ENQUIRY,
                "Requirement posted",
                "No matching vendor is available yet. Your requirement remains open.",
                "/customer?tab=requirements");
    }

    private String normalize(String value) {
        return nullToEmpty(value)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "the requested location";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
