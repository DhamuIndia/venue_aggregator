package com.staminal.venue.requirements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.CustomerRequirementStatus;
import com.staminal.venue.enums.PreferredContactChannel;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.leads.VendorLeadRepository;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.notifications.NotificationType;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

@ExtendWith(MockitoExtension.class)
class RequirementMatchingServiceTest {

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private VendorLeadRepository vendorLeadRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditService auditService;

    private RequirementMatchingService service;

    @BeforeEach
    void setUp() {
        service = new RequirementMatchingService(
                vendorRepository,
                vendorLeadRepository,
                notificationService,
                auditService);
    }

    @Test
    void distributesOnePrivacySafeLeadPerEligibleVendor() {
        VendorCategory photography = category(1L, "Photography");
        VendorCategory makeup = category(6L, "Makeup");
        CustomerRequirement requirement = requirement(Set.of(photography, makeup), false);
        Vendors vendor = vendor(
                501L,
                VendorStatus.APPROVED,
                "ACTIVE",
                "Chennai",
                "600040",
                Set.of(photography, makeup));

        when(vendorRepository.findByStatus(VendorStatus.APPROVED)).thenReturn(List.of(vendor));
        when(vendorLeadRepository.existsByRequirement_IdAndVendor_Id(801L, 501L)).thenReturn(false);
        when(vendorLeadRepository.save(any(VendorLead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RequirementMatchResult result = service.distribute(requirement);

        ArgumentCaptor<VendorLead> leadCaptor = ArgumentCaptor.forClass(VendorLead.class);
        verify(vendorLeadRepository).save(leadCaptor.capture());
        VendorLead lead = leadCaptor.getValue();
        assertThat(result).isEqualTo(new RequirementMatchResult(1, 1));
        assertThat(lead.getRequirement()).isSameAs(requirement);
        assertThat(lead.getVendor()).isSameAs(vendor);
        assertThat(lead.getService()).isEqualTo("Makeup, Photography");
        assertThat(lead.getLocation()).isEqualTo("Adyar, Chennai");
        assertThat(lead.getBudget()).isEqualByComparingTo("150000");
        assertThat(lead.getCustomerName()).isEqualTo("VenueMart customer");
        assertThat(lead.getCustomerPhone()).isNull();
        assertThat(lead.getCustomerEmail()).isNull();
        verify(notificationService, times(2)).notifyUser(
                any(User.class),
                eq(NotificationType.ENQUIRY),
                any(String.class),
                any(String.class),
                any(String.class));
        verify(auditService).record(any(AuditCommand.class));
    }

    @Test
    void sharesContactDetailsOnlyWhenCustomerConsents() {
        VendorCategory photography = category(1L, "Photography");
        CustomerRequirement requirement = requirement(Set.of(photography), true);
        Vendors vendor = vendor(
                501L,
                VendorStatus.APPROVED,
                "ACTIVE",
                "Chennai",
                "600020",
                Set.of(photography));

        when(vendorRepository.findByStatus(VendorStatus.APPROVED)).thenReturn(List.of(vendor));
        when(vendorLeadRepository.existsByRequirement_IdAndVendor_Id(801L, 501L)).thenReturn(false);
        when(vendorLeadRepository.save(any(VendorLead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.distribute(requirement);

        ArgumentCaptor<VendorLead> leadCaptor = ArgumentCaptor.forClass(VendorLead.class);
        verify(vendorLeadRepository).save(leadCaptor.capture());
        assertThat(leadCaptor.getValue().getCustomerName()).isEqualTo("Priya Raman");
        assertThat(leadCaptor.getValue().getCustomerPhone()).isEqualTo("9000000001");
        assertThat(leadCaptor.getValue().getCustomerEmail()).isEqualTo("priya@example.com");
    }

    @Test
    void excludesInactiveUnrelatedAndOutOfAreaVendors() {
        VendorCategory photography = category(1L, "Photography");
        VendorCategory catering = category(8L, "Catering");
        CustomerRequirement requirement = requirement(Set.of(photography), false);
        Vendors samePincode = vendor(
                501L,
                VendorStatus.APPROVED,
                "ACTIVE",
                "Tambaram",
                "600020",
                Set.of(photography));
        Vendors inactive = vendor(
                502L,
                VendorStatus.APPROVED,
                "SUSPENDED",
                "Chennai",
                "600020",
                Set.of(photography));
        Vendors unrelated = vendor(
                503L,
                VendorStatus.APPROVED,
                "ACTIVE",
                "Chennai",
                "600020",
                Set.of(catering));
        Vendors outOfArea = vendor(
                504L,
                VendorStatus.APPROVED,
                "ACTIVE",
                "Coimbatore",
                "641001",
                Set.of(photography));

        when(vendorRepository.findByStatus(VendorStatus.APPROVED))
                .thenReturn(List.of(samePincode, inactive, unrelated, outOfArea));
        when(vendorLeadRepository.existsByRequirement_IdAndVendor_Id(801L, 501L)).thenReturn(false);
        when(vendorLeadRepository.save(any(VendorLead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RequirementMatchResult result = service.distribute(requirement);

        assertThat(result).isEqualTo(new RequirementMatchResult(1, 1));
        ArgumentCaptor<VendorLead> leadCaptor = ArgumentCaptor.forClass(VendorLead.class);
        verify(vendorLeadRepository).save(leadCaptor.capture());
        assertThat(leadCaptor.getValue().getVendor().getId()).isEqualTo(501L);
    }

    @Test
    void retryDoesNotDuplicateLeadOrNotification() {
        VendorCategory photography = category(1L, "Photography");
        CustomerRequirement requirement = requirement(Set.of(photography), false);
        Vendors vendor = vendor(
                501L,
                VendorStatus.APPROVED,
                "ACTIVE",
                "Chennai",
                "600020",
                Set.of(photography));

        when(vendorRepository.findByStatus(VendorStatus.APPROVED)).thenReturn(List.of(vendor));
        when(vendorLeadRepository.existsByRequirement_IdAndVendor_Id(801L, 501L)).thenReturn(true);

        RequirementMatchResult result = service.distribute(requirement);

        assertThat(result).isEqualTo(new RequirementMatchResult(1, 0));
        verify(vendorLeadRepository, never()).save(any());
        verify(notificationService, never()).notifyUser(any(), any(), any(), any(), any());
        verify(auditService).record(any(AuditCommand.class));
    }

    @Test
    void noMatchKeepsRequirementOpenAndNotifiesCustomer() {
        CustomerRequirement requirement = requirement(Set.of(category(1L, "Photography")), false);
        when(vendorRepository.findByStatus(VendorStatus.APPROVED)).thenReturn(List.of());

        RequirementMatchResult result = service.distribute(requirement);

        assertThat(result).isEqualTo(new RequirementMatchResult(0, 0));
        assertThat(requirement.getStatus()).isEqualTo(CustomerRequirementStatus.OPEN);
        verify(notificationService).notifyUser(
                requirement.getCustomer(),
                NotificationType.ENQUIRY,
                "Requirement posted",
                "No matching vendor is available yet. Your requirement remains open.",
                "/customer?tab=requirements");
        verify(vendorLeadRepository, never()).save(any());
    }

    private CustomerRequirement requirement(Set<VendorCategory> categories, boolean shareContactDetails) {
        CustomerRequirement requirement = new CustomerRequirement();
        requirement.setId(801L);
        requirement.setCustomer(customer());
        requirement.setEventType("Wedding");
        requirement.setEventDate(LocalDate.parse("2026-09-12"));
        requirement.setLocation("Adyar");
        requirement.setCity("Chennai");
        requirement.setPincode("600020");
        requirement.setBudgetMin(new BigDecimal("75000"));
        requirement.setBudgetMax(new BigDecimal("150000"));
        requirement.setDetails("Candid coverage and bridal portraits.");
        requirement.setPreferredContactChannel(PreferredContactChannel.WHATSAPP);
        requirement.setShareContactDetails(shareContactDetails);
        requirement.setStatus(CustomerRequirementStatus.OPEN);
        requirement.setServiceCategories(categories);
        return requirement;
    }

    private Vendors vendor(
            Long id,
            VendorStatus status,
            String userStatus,
            String city,
            String pincode,
            Set<VendorCategory> categories) {
        User user = new User();
        user.setId(id + 1000);
        user.setStatus(userStatus);
        Vendors vendor = new Vendors();
        vendor.setId(id);
        vendor.setBusinessName("Vendor " + id);
        vendor.setStatus(status);
        vendor.setUser(user);
        vendor.setCity(city);
        vendor.setPincode(pincode);
        vendor.setCategories(categories);
        return vendor;
    }

    private User customer() {
        User customer = new User();
        customer.setId(101L);
        customer.setFullName("Priya Raman");
        customer.setPhone("9000000001");
        customer.setEmail("priya@example.com");
        customer.setStatus("ACTIVE");
        return customer;
    }

    private VendorCategory category(Long id, String name) {
        VendorCategory category = new VendorCategory();
        category.setId(id);
        category.setCategoryName(name);
        return category;
    }
}
