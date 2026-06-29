package com.staminal.venue.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

@ExtendWith(MockitoExtension.class)
class AdminVendorModerationServiceTest {

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private UserRepository userRepository;

    private AdminVendorModerationService adminVendorModerationService;

    @BeforeEach
    void setUp() {
        adminVendorModerationService = new AdminVendorModerationService(vendorRepository, userRepository);
    }

    @Test
    void listPendingVendorApplicationsReturnsFrontendShape() {
        Vendors vendor = vendor(501L, VendorStatus.PENDING);

        when(vendorRepository.findByStatus(VendorStatus.PENDING)).thenReturn(List.of(vendor));

        AdminVendorListResponse response = adminVendorModerationService.getVendors("PENDING_APPROVAL", 0, 50);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        AdminVendorResponse item = response.content().get(0);
        assertThat(item.id()).isEqualTo("501");
        assertThat(item.businessName()).isEqualTo("Saffron Leaf Catering");
        assertThat(item.category()).isEqualTo("Catering");
        assertThat(item.status()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void approvePendingVendorStoresReviewerAndTimestamp() {
        Vendors vendor = vendor(501L, VendorStatus.PENDING);
        User admin = admin();

        when(vendorRepository.findById(501L)).thenReturn(Optional.of(vendor));
        when(userRepository.findById(900L)).thenReturn(Optional.of(admin));
        when(vendorRepository.save(any(Vendors.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminVendorResponse response = adminVendorModerationService.reviewVendor(
                "501",
                new AdminReviewRequest("APPROVED", "Business identity verified"),
                auth());

        ArgumentCaptor<Vendors> vendorCaptor = ArgumentCaptor.forClass(Vendors.class);
        verify(vendorRepository).save(vendorCaptor.capture());

        Vendors saved = vendorCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(VendorStatus.APPROVED);
        assertThat(saved.getRejectionReason()).isNull();
        assertThat(saved.getReviewedByUser()).isSameAs(admin);
        assertThat(saved.getReviewedAt()).isNotNull();
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.reviewedBy()).isEqualTo("Test Admin");
        assertThat(response.reviewedAt()).isNotNull();
    }

    @Test
    void rejectRequiresReason() {
        Vendors vendor = vendor(501L, VendorStatus.PENDING);

        when(vendorRepository.findById(501L)).thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> adminVendorModerationService.reviewVendor(
                "501",
                new AdminReviewRequest("REJECTED", " "),
                auth()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void approvedVendorCannotBeReviewedAgain() {
        Vendors vendor = vendor(501L, VendorStatus.APPROVED);

        when(vendorRepository.findById(501L)).thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> adminVendorModerationService.reviewVendor(
                "501",
                new AdminReviewRequest("APPROVED", "Already verified"),
                auth()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    private Vendors vendor(Long id, VendorStatus status) {
        Vendors vendor = new Vendors();
        vendor.setId(id);
        vendor.setVendorName("Manoj Krishnan");
        vendor.setBusinessName("Saffron Leaf Catering");
        vendor.setCity("Chennai");
        vendor.setCategories(Set.of(category("Catering")));
        vendor.setStatus(status);
        vendor.setCreatedAt(Instant.parse("2026-06-20T10:00:00Z"));
        vendor.setUpdatedAt(Instant.parse("2026-06-21T10:00:00Z"));
        return vendor;
    }

    private VendorCategory category(String name) {
        VendorCategory category = new VendorCategory();
        category.setId(8L);
        category.setCategoryName(name);
        return category;
    }

    private User admin() {
        User admin = new User();
        admin.setId(900L);
        admin.setFullName("Test Admin");
        admin.setPhone("9000000001");
        admin.setEmail("admin@example.com");
        admin.setStatus("ACTIVE");
        admin.setPasswordHash("hashed-password");
        return admin;
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(
                "900",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
