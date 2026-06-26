package com.staminal.venue.vendors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.staminal.venue.auth.service.JwtService;
import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Dto.UpdateVendorRequest;
import com.staminal.venue.vendors.Dto.VendorResponse;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorCategoryRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;
import com.staminal.venue.vendors.Service.VendorService;

@ExtendWith(MockitoExtension.class)
class VendorServiceTest {

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private VendorCategoryRepository vendorCategoryRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    private VendorService vendorService;

    @BeforeEach
    void setUp() {
        vendorService = new VendorService(vendorRepository, vendorCategoryRepository, jwtService, userRepository);
    }

    @Test
    void updateProfileCreatesDraftForRegisteredVendorUser() {
        User user = user();
        UpdateVendorRequest request = updateRequest();

        when(userRepository.findById(301L)).thenReturn(Optional.of(user));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.empty());
        when(vendorCategoryRepository.findAll()).thenReturn(List.of(category("Catering")));
        when(vendorRepository.save(any(Vendors.class))).thenAnswer(invocation -> {
            Vendors vendor = invocation.getArgument(0);
            vendor.setId(501L);
            return vendor;
        });

        VendorResponse response = vendorService.updateProfile("301", request);

        ArgumentCaptor<Vendors> vendorCaptor = ArgumentCaptor.forClass(Vendors.class);
        verify(vendorRepository).save(vendorCaptor.capture());

        Vendors savedVendor = vendorCaptor.getValue();
        assertThat(savedVendor.getUser()).isSameAs(user);
        assertThat(savedVendor.getBusinessName()).isEqualTo("Saffron Leaf Catering");
        assertThat(savedVendor.getStatus()).isEqualTo(VendorStatus.DRAFT);
        assertThat(savedVendor.getCategories()).extracting(VendorCategory::getCategoryName).containsExactly("Catering");
        assertThat(response.getStatus()).isEqualTo("DRAFT");
        assertThat(response.getCategory()).isEqualTo("CATERING");
    }

    @Test
    void submitProfileMovesDraftToPendingApprovalWithoutRequestBody() {
        Vendors vendor = savedVendor();

        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorRepository.save(any(Vendors.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VendorResponse response = vendorService.submitProfile("301");

        assertThat(vendor.getStatus()).isEqualTo(VendorStatus.PENDING);
        assertThat(response.getStatus()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void getProfileReturnsDraftShapeWhenVendorRowDoesNotExistYet() {
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.empty());
        when(userRepository.findById(301L)).thenReturn(Optional.of(user()));

        VendorResponse response = vendorService.getProfile("301");

        assertThat(response.getStatus()).isEqualTo("DRAFT");
        assertThat(response.getCategory()).isEqualTo("CATERING");
        assertThat(response.getBusinessName()).isEqualTo("Test Vendor");
    }

    private User user() {
        User user = new User();
        user.setId(301L);
        user.setFullName("Test Vendor");
        user.setPhone("9884012345");
        user.setEmail("test.vendor@example.com");
        user.setPasswordHash("hashed-password");
        user.setStatus("ACTIVE");
        return user;
    }

    private UpdateVendorRequest updateRequest() {
        UpdateVendorRequest request = new UpdateVendorRequest();
        request.setBusinessName("Saffron Leaf Catering");
        request.setCategory("CATERING");
        request.setCity("Chennai");
        request.setArea("T Nagar");
        request.setServiceRadius(25);
        request.setYearsInBusiness(5);
        request.setDescription("Premium event catering service");
        request.setServices(List.of("Wedding service", "Reception service"));
        request.setPackageName("Wedding essentials");
        request.setStartingPrice(new BigDecimal("45000"));
        request.setPackageDescription("Starter package details");
        return request;
    }

    private Vendors savedVendor() {
        Vendors vendor = new Vendors();
        vendor.setId(501L);
        vendor.setUser(user());
        vendor.setVendorName("Test Vendor");
        vendor.setBusinessName("Saffron Leaf Catering");
        vendor.setCity("Chennai");
        vendor.setArea("T Nagar");
        vendor.setCoverImageUrl("");
        vendor.setAddressLine("");
        vendor.setEmail("test.vendor@example.com");
        vendor.setPasswordHash("hashed-password");
        vendor.setServices(List.of("Wedding service"));
        vendor.setPackageName("Wedding essentials");
        vendor.setStartingPrice(new BigDecimal("45000"));
        vendor.setStatus(VendorStatus.DRAFT);
        vendor.setCategories(java.util.Set.of(category("Catering")));
        vendor.setUpdatedAt(Instant.now());
        return vendor;
    }

    private VendorCategory category(String name) {
        VendorCategory category = new VendorCategory();
        category.setId(8L);
        category.setCategoryName(name);
        return category;
    }
}
