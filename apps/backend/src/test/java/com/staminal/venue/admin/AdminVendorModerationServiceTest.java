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

import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorMediaRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;
import com.staminal.venue.vendors.Service.VendorService;

@ExtendWith(MockitoExtension.class)
class AdminVendorModerationServiceTest {

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private VendorService vendorService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VendorMediaRepository vendorMediaRepository;

    private AdminVendorModerationService adminVendorModerationService;

    @BeforeEach
    void setUp() {
        adminVendorModerationService = new AdminVendorModerationService(
                vendorRepository,
                vendorMediaRepository,
                auditService,
                adminRepository,
                userRepository,
                vendorService);
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
        Admin admin = admin();

        when(vendorRepository.findById(501L)).thenReturn(Optional.of(vendor));
        when(adminRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
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
        assertThat(saved.getReviewedByAdmin()).isSameAs(admin);
        assertThat(saved.getReviewedAt()).isNotNull();
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.reviewedBy()).isEqualTo(900L);
        assertThat(response.reviewedAt()).isNotNull();
    }

    @Test
    void approvePendingVendorWorksWithUnifiedNumericSuperAdminSession() {
        Vendors vendor = vendor(502L, VendorStatus.PENDING);
        User superAdminUser = adminUser(901L, "super@example.com");
        Admin superAdmin = admin(901L, "super@example.com");

        when(vendorRepository.findById(502L)).thenReturn(Optional.of(vendor));
        when(userRepository.findById(901L)).thenReturn(Optional.of(superAdminUser));
        when(adminRepository.findByEmail("super@example.com")).thenReturn(Optional.of(superAdmin));
        when(vendorRepository.save(any(Vendors.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminVendorResponse response = adminVendorModerationService.reviewVendor(
                "502",
                new AdminReviewRequest("APPROVED", "Business identity verified"),
                auth("901", "ROLE_SUPER_ADMIN"));

        ArgumentCaptor<Vendors> vendorCaptor = ArgumentCaptor.forClass(Vendors.class);
        verify(vendorRepository).save(vendorCaptor.capture());

        Vendors saved = vendorCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(VendorStatus.APPROVED);
        assertThat(saved.getReviewedByAdmin()).isSameAs(superAdmin);
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.reviewedBy()).isEqualTo(901L);
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

    private Admin admin() {
        return admin(900L, "admin@example.com");
    }

    private Admin admin(Long id, String email) {
        Admin admin = new Admin();
        admin.setId(id);
        admin.setFullName("Test Admin");
        admin.setContactNumber("9000000001");
        admin.setEmail(email);
        admin.setStatus("ACTIVE");
        admin.setPasswordHash("hashed-password");
        return admin;
    }

    private User adminUser(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setFullName("Test Admin");
        user.setPhone("9000000001");
        user.setEmail(email);
        user.setStatus("ACTIVE");
        user.setPasswordHash("hashed-password");
        return user;
    }

    private UsernamePasswordAuthenticationToken auth() {
        return auth("admin@example.com", "ROLE_ADMIN");
    }

    private UsernamePasswordAuthenticationToken auth(String principal, String role) {
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(role)));
    }
}
