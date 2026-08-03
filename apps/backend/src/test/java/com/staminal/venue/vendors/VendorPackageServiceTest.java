package com.staminal.venue.vendors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.enums.VendorServiceType;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.vendors.Dto.VendorPackageListResponse;
import com.staminal.venue.vendors.Dto.VendorPackageResponse;
import com.staminal.venue.vendors.Dto.VendorPackageUpsertRequest;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.VendorPackage;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorPackageRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;
import com.staminal.venue.vendors.Service.VendorPackageService;

@ExtendWith(MockitoExtension.class)
class VendorPackageServiceTest {

    @Mock
    private VendorPackageRepository vendorPackageRepository;

    @Mock
    private VendorRepository vendorRepository;

    private VendorPackageService vendorPackageService;

    @BeforeEach
    void setUp() {
        vendorPackageService = new VendorPackageService(vendorPackageRepository, vendorRepository);
    }

    @Test
    void getMyPackagesReturnsOnlyPackagesForAuthenticatedVendor() {
        Vendors vendor = vendor(501L, 301L);
        VendorPackage packageItem = packageItem(801L, vendor, "Grand wedding feast", "1050");
        packageItem.setIncludes(List.of("Two welcome drinks", "28-item meal"));

        when(vendorPackageRepository.findByVendor_User_Id(301L)).thenReturn(List.of(packageItem));

        VendorPackageListResponse response = vendorPackageService.getMyPackages(authentication());

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).getName()).isEqualTo("Grand wedding feast");
        assertThat(response.content().get(0).getIncludes()).containsExactly("Two welcome drinks", "28-item meal");
    }

    @Test
    void createMyPackageUsesAuthenticatedVendorAndStoresIncludes() {
        Vendors vendor = vendor(501L, 301L);
        VendorPackageUpsertRequest request = new VendorPackageUpsertRequest(
                " Grand wedding feast ",
                null,
                "Expanded wedding menu",
                new BigDecimal("1050"),
                List.of(" Two welcome drinks ", "", "28-item meal"));

        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorPackageRepository.save(any(VendorPackage.class))).thenAnswer(invocation -> {
            VendorPackage saved = invocation.getArgument(0);
            saved.setId(801L);
            return saved;
        });
        when(vendorPackageRepository.findByVendor_Id(501L)).thenReturn(List.of());
        when(vendorRepository.save(any(Vendors.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VendorPackageResponse response = vendorPackageService.createMyPackage(request, authentication());

        ArgumentCaptor<VendorPackage> packageCaptor = ArgumentCaptor.forClass(VendorPackage.class);
        verify(vendorPackageRepository).save(packageCaptor.capture());

        VendorPackage savedPackage = packageCaptor.getValue();
        assertThat(savedPackage.getVendor()).isSameAs(vendor);
        assertThat(savedPackage.getPackageName()).isEqualTo("Grand wedding feast");
        assertThat(savedPackage.getIncludes()).containsExactly("Two welcome drinks", "28-item meal");
        assertThat(savedPackage.getServiceType()).isEqualTo(VendorServiceType.CATERING);
        assertThat(savedPackage.getServiceId()).isEqualTo(501L);
        assertThat(vendor.getPackageName()).isEqualTo("Grand wedding feast");
        assertThat(vendor.getStartingPrice()).isEqualByComparingTo("1050");
        assertThat(response.getName()).isEqualTo("Grand wedding feast");
    }

    @Test
    void updateMyPackageRejectsPackageOwnedByAnotherVendor() {
        Vendors otherVendor = vendor(777L, 999L);
        VendorPackage packageItem = packageItem(801L, otherVendor, "Other package", "900");

        when(vendorPackageRepository.findByIdAndVendor_User_Id(801L, 301L)).thenReturn(Optional.empty());
        when(vendorPackageRepository.findById(801L)).thenReturn(Optional.of(packageItem));

        VendorPackageUpsertRequest request = new VendorPackageUpsertRequest(
                "Grand wedding feast",
                null,
                "Expanded wedding menu",
                new BigDecimal("1050"),
                List.of("Two welcome drinks"));

        assertThatThrownBy(() -> vendorPackageService.updateMyPackage("801", request, authentication()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void deleteMyPackageRemovesOwnedPackageAndClearsVendorSummaryWhenNoPackagesRemain() {
        Vendors vendor = vendor(501L, 301L);
        vendor.setPackageName("Grand wedding feast");
        vendor.setPackageDescription("Expanded wedding menu");
        vendor.setStartingPrice(new BigDecimal("1050"));
        VendorPackage packageItem = packageItem(801L, vendor, "Grand wedding feast", "1050");

        when(vendorPackageRepository.findByIdAndVendor_User_Id(801L, 301L)).thenReturn(Optional.of(packageItem));
        when(vendorPackageRepository.findByVendor_Id(501L)).thenReturn(List.of());
        when(vendorRepository.save(any(Vendors.class))).thenAnswer(invocation -> invocation.getArgument(0));

        vendorPackageService.deleteMyPackage("801", authentication());

        verify(vendorPackageRepository).delete(packageItem);
        assertThat(vendor.getPackageName()).isNull();
        assertThat(vendor.getPackageDescription()).isNull();
        assertThat(vendor.getStartingPrice()).isNull();
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                "301",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));
    }

    private Vendors vendor(Long vendorId, Long userId) {
        User user = new User();
        user.setId(userId);

        Vendors vendor = new Vendors();
        vendor.setId(vendorId);
        vendor.setUser(user);
        vendor.setBusinessName("Saffron Leaf Catering");
        vendor.setCategories(Set.of(category("Catering")));
        vendor.setServices(List.of("Wedding catering"));
        return vendor;
    }

    private VendorPackage packageItem(Long id, Vendors vendor, String name, String price) {
        VendorPackage packageItem = new VendorPackage();
        packageItem.setId(id);
        packageItem.setVendor(vendor);
        packageItem.setPackageName(name);
        packageItem.setDescription("Starter package details");
        packageItem.setPrice(new BigDecimal(price));
        packageItem.setCreatedAt(Instant.parse("2026-06-01T10:00:00Z"));
        return packageItem;
    }

    private VendorCategory category(String name) {
        VendorCategory category = new VendorCategory();
        category.setId(8L);
        category.setCategoryName(name);
        return category;
    }
}
