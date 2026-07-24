package com.staminal.venue.requirements;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.CustomerRequirementStatus;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.requirements.dto.CreateCustomerRequirementRequest;
import com.staminal.venue.requirements.dto.CustomerRequirementCategoryResponse;
import com.staminal.venue.requirements.dto.CustomerRequirementOptionsResponse;
import com.staminal.venue.requirements.dto.CustomerRequirementResponse;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Repository.VendorCategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class CustomerRequirementService {

    private final CustomerRequirementRepository customerRequirementRepository;
    private final VendorCategoryRepository vendorCategoryRepository;
    private final UserRepository userRepository;
    private final MarketplaceRequirementProperties properties;
    private final AuditService auditService;
    private final RequirementMatchingService requirementMatchingService;

    @Transactional(readOnly = true)
    public CustomerRequirementOptionsResponse getOptions() {
        if (!properties.isEnabled()) {
            return new CustomerRequirementOptionsResponse(false, List.of());
        }

        List<CustomerRequirementCategoryResponse> categories = vendorCategoryRepository
                .findAllByOrderByCategoryNameAsc()
                .stream()
                .filter(this::isMarketplaceService)
                .map(this::mapCategory)
                .toList();
        return new CustomerRequirementOptionsResponse(true, categories);
    }

    public CustomerRequirementResponse create(
            CreateCustomerRequirementRequest request,
            Authentication authentication) {
        requireEnabled();
        User customer = currentCustomer(authentication);
        validateBudgetRange(request.budgetMin(), request.budgetMax());

        Set<Long> requestedCategoryIds = new LinkedHashSet<>(request.categoryIds());
        List<VendorCategory> categories = vendorCategoryRepository.findAllById(requestedCategoryIds);
        if (categories.size() != requestedCategoryIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more service categories are invalid");
        }
        if (categories.stream().anyMatch(category -> !isMarketplaceService(category))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select vendor services only");
        }

        CustomerRequirement requirement = new CustomerRequirement();
        requirement.setCustomer(customer);
        requirement.setEventType(request.eventType().trim());
        requirement.setEventDate(request.eventDate());
        requirement.setLocation(request.location().trim());
        requirement.setCity(trimToNull(request.city()));
        requirement.setPincode(trimToNull(request.pincode()));
        requirement.setBudgetMin(request.budgetMin());
        requirement.setBudgetMax(request.budgetMax());
        requirement.setGuestCount(request.guestCount());
        requirement.setDetails(trimToNull(request.details()));
        requirement.setPreferredContactChannel(request.preferredContactChannel());
        requirement.setShareContactDetails(request.shareContactDetails());
        requirement.setStatus(CustomerRequirementStatus.OPEN);
        requirement.setServiceCategories(new LinkedHashSet<>(categories));

        CustomerRequirement saved = customerRequirementRepository.save(requirement);
        auditService.record(new AuditCommand(
                customer.getId(),
                UserRole.CUSTOMER.name(),
                AuditAction.REQUIREMENT_CREATED,
                "CUSTOMER_REQUIREMENT",
                String.valueOf(saved.getId()),
                "Customer posted a marketplace requirement",
                null,
                Map.of(
                        "eventDate", saved.getEventDate().toString(),
                        "serviceCount", saved.getServiceCategories().size(),
                        "status", saved.getStatus().name()),
                null));

        requirementMatchingService.distribute(saved);
        return mapResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CustomerRequirementResponse> getMine(Authentication authentication) {
        requireEnabled();
        User customer = currentCustomer(authentication);
        return customerRequirementRepository
                .findByCustomer_IdOrderByCreatedAtDesc(customer.getId())
                .stream()
                .map(this::mapResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerRequirementResponse getMine(Long requirementId, Authentication authentication) {
        requireEnabled();
        User customer = currentCustomer(authentication);
        CustomerRequirement requirement = customerRequirementRepository
                .findByIdAndCustomer_Id(requirementId, customer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requirement not found"));
        return mapResponse(requirement);
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Marketplace requirements are not available");
        }
    }

    private User currentCustomer(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!hasRole(authentication, UserRole.CUSTOMER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CUSTOMER role is required");
        }

        Long userId;
        try {
            userId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user session");
        }

        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is not active");
        }
        return customer;
    }

    private boolean hasRole(Authentication authentication, UserRole role) {
        String authority = "ROLE_" + role.name();
        return authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private void validateBudgetRange(BigDecimal minimum, BigDecimal maximum) {
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Minimum budget cannot be greater than maximum budget");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private CustomerRequirementCategoryResponse mapCategory(VendorCategory category) {
        return new CustomerRequirementCategoryResponse(category.getId(), category.getCategoryName());
    }

    private boolean isMarketplaceService(VendorCategory category) {
        return category.getCategoryName() != null
                && !"hall".equalsIgnoreCase(category.getCategoryName().trim());
    }

    private CustomerRequirementResponse mapResponse(CustomerRequirement requirement) {
        List<CustomerRequirementCategoryResponse> services = requirement.getServiceCategories()
                .stream()
                .sorted(Comparator.comparing(VendorCategory::getCategoryName, String.CASE_INSENSITIVE_ORDER))
                .map(this::mapCategory)
                .toList();
        return new CustomerRequirementResponse(
                requirement.getId(),
                requirement.getEventType(),
                requirement.getEventDate(),
                requirement.getLocation(),
                requirement.getCity(),
                requirement.getPincode(),
                requirement.getBudgetMin(),
                requirement.getBudgetMax(),
                requirement.getGuestCount(),
                requirement.getDetails(),
                requirement.getPreferredContactChannel(),
                requirement.isShareContactDetails(),
                requirement.getStatus(),
                services,
                requirementMatchingService.countLeads(requirement.getId()),
                requirement.getCreatedAt(),
                requirement.getUpdatedAt());
    }
}
