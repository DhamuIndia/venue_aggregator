package com.staminal.venue.requirements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.CustomerRequirementStatus;
import com.staminal.venue.enums.PreferredContactChannel;
import com.staminal.venue.requirements.dto.CreateCustomerRequirementRequest;
import com.staminal.venue.requirements.dto.CustomerRequirementOptionsResponse;
import com.staminal.venue.requirements.dto.CustomerRequirementResponse;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Repository.VendorCategoryRepository;

@ExtendWith(MockitoExtension.class)
class CustomerRequirementServiceTest {

    @Mock
    private CustomerRequirementRepository customerRequirementRepository;

    @Mock
    private VendorCategoryRepository vendorCategoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private CustomerRequirementService service;

    @BeforeEach
    void setUp() {
        service = service(true);
    }

    @Test
    void optionsAreEmptyWhenFeatureIsDisabled() {
        CustomerRequirementOptionsResponse response = service(false).getOptions();

        assertThat(response.enabled()).isFalse();
        assertThat(response.categories()).isEmpty();
        verify(vendorCategoryRepository, never()).findAllByOrderByCategoryNameAsc();
    }

    @Test
    void optionsExposeSortedConfiguredCategoriesWhenEnabled() {
        when(vendorCategoryRepository.findAllByOrderByCategoryNameAsc())
                .thenReturn(List.of(category(2L, "Catering"), category(4L, "Hall"), category(1L, "Photography")));

        CustomerRequirementOptionsResponse response = service.getOptions();

        assertThat(response.enabled()).isTrue();
        assertThat(response.categories()).extracting(item -> item.name())
                .containsExactly("Catering", "Photography");
    }

    @Test
    void createRejectsHallAsAServiceCategory() {
        when(userRepository.findById(101L)).thenReturn(Optional.of(customer()));
        when(vendorCategoryRepository.findAllById(Set.of(4L)))
                .thenReturn(List.of(category(4L, "Hall")));

        assertThatThrownBy(() -> service.create(request(Set.of(4L)), customerAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo("Select vendor services only"));
        verify(customerRequirementRepository, never()).save(any());
    }

    @Test
    void createStoresRequirementWithoutGeneratingVendorLeads() {
        User customer = customer();
        VendorCategory photography = category(1L, "Photography");
        VendorCategory makeup = category(6L, "Makeup");
        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(vendorCategoryRepository.findAllById(Set.of(1L, 6L)))
                .thenReturn(List.of(photography, makeup));
        when(customerRequirementRepository.save(any(CustomerRequirement.class))).thenAnswer(invocation -> {
            CustomerRequirement requirement = invocation.getArgument(0);
            requirement.setId(501L);
            requirement.onCreate();
            return requirement;
        });

        CustomerRequirementResponse response = service.create(request(Set.of(1L, 6L)), customerAuth());

        ArgumentCaptor<CustomerRequirement> requirementCaptor = ArgumentCaptor.forClass(CustomerRequirement.class);
        verify(customerRequirementRepository).save(requirementCaptor.capture());
        CustomerRequirement saved = requirementCaptor.getValue();
        assertThat(saved.getCustomer()).isSameAs(customer);
        assertThat(saved.getServiceCategories()).containsExactlyInAnyOrder(photography, makeup);
        assertThat(saved.getStatus()).isEqualTo(CustomerRequirementStatus.OPEN);
        assertThat(saved.isShareContactDetails()).isFalse();
        assertThat(response.id()).isEqualTo(501L);
        assertThat(response.services()).extracting(item -> item.name())
                .containsExactly("Makeup", "Photography");
        verify(auditService).record(any(AuditCommand.class));
    }

    @Test
    void createRejectsUnknownCategoryWithoutSaving() {
        when(userRepository.findById(101L)).thenReturn(Optional.of(customer()));
        when(vendorCategoryRepository.findAllById(Set.of(1L, 99L)))
                .thenReturn(List.of(category(1L, "Photography")));

        assertThatThrownBy(() -> service.create(request(Set.of(1L, 99L)), customerAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(customerRequirementRepository, never()).save(any());
    }

    @Test
    void createRejectsInvalidBudgetRange() {
        CreateCustomerRequirementRequest request = new CreateCustomerRequirementRequest(
                Set.of(1L),
                "Wedding",
                LocalDate.now().plusDays(30),
                "Adyar",
                "Chennai",
                "600020",
                new BigDecimal("200000"),
                new BigDecimal("100000"),
                300,
                "Candid wedding coverage.",
                PreferredContactChannel.IN_APP,
                false);
        when(userRepository.findById(101L)).thenReturn(Optional.of(customer()));

        assertThatThrownBy(() -> service.create(request, customerAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo("Minimum budget cannot be greater than maximum budget"));
        verify(vendorCategoryRepository, never()).findAllById(any());
    }

    @Test
    void featureFlagBlocksCustomerWrites() {
        assertThatThrownBy(() -> service(false).create(request(Set.of(1L)), customerAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(customerRequirementRepository, never()).save(any());
    }

    @Test
    void onlyCustomersCanCreateRequirements() {
        assertThatThrownBy(() -> service.create(request(Set.of(1L)), vendorAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(customerRequirementRepository, never()).save(any());
    }

    @Test
    void listReturnsOnlyAuthenticatedCustomersRequirements() {
        CustomerRequirement requirement = storedRequirement();
        when(userRepository.findById(101L)).thenReturn(Optional.of(customer()));
        when(customerRequirementRepository.findByCustomer_IdOrderByCreatedAtDesc(101L))
                .thenReturn(List.of(requirement));

        List<CustomerRequirementResponse> response = service.getMine(customerAuth());

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(501L);
        verify(customerRequirementRepository).findByCustomer_IdOrderByCreatedAtDesc(101L);
    }

    @Test
    void detailDoesNotExposeAnotherCustomersRequirement() {
        when(userRepository.findById(101L)).thenReturn(Optional.of(customer()));
        when(customerRequirementRepository.findByIdAndCustomer_Id(501L, 101L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMine(501L, customerAuth()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private CustomerRequirementService service(boolean enabled) {
        return new CustomerRequirementService(
                customerRequirementRepository,
                vendorCategoryRepository,
                userRepository,
                new MarketplaceRequirementProperties(enabled),
                auditService);
    }

    private CreateCustomerRequirementRequest request(Set<Long> categoryIds) {
        return new CreateCustomerRequirementRequest(
                categoryIds,
                "Wedding",
                LocalDate.now().plusDays(30),
                "Adyar",
                "Chennai",
                "600020",
                new BigDecimal("75000"),
                new BigDecimal("150000"),
                300,
                "Candid wedding coverage.",
                PreferredContactChannel.IN_APP,
                false);
    }

    private CustomerRequirement storedRequirement() {
        CustomerRequirement requirement = new CustomerRequirement();
        requirement.setId(501L);
        requirement.setCustomer(customer());
        requirement.setEventType("Wedding");
        requirement.setEventDate(LocalDate.now().plusDays(30));
        requirement.setLocation("Adyar");
        requirement.setCity("Chennai");
        requirement.setPreferredContactChannel(PreferredContactChannel.IN_APP);
        requirement.setStatus(CustomerRequirementStatus.OPEN);
        requirement.setServiceCategories(Set.of(category(1L, "Photography")));
        requirement.onCreate();
        return requirement;
    }

    private VendorCategory category(Long id, String name) {
        VendorCategory category = new VendorCategory();
        category.setId(id);
        category.setCategoryName(name);
        return category;
    }

    private User customer() {
        User user = new User();
        user.setId(101L);
        user.setFullName("Priya Raman");
        user.setPhone("9000000001");
        user.setEmail("priya@example.com");
        user.setStatus("ACTIVE");
        return user;
    }

    private Authentication customerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "101",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }

    private Authentication vendorAuth() {
        return new UsernamePasswordAuthenticationToken(
                "301",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));
    }
}
