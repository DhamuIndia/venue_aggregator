package com.staminal.venue.vendors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.reviews.VendorReview.VendorReview;
import com.staminal.venue.reviews.HallReview.ReviewModerationStatus;
import com.staminal.venue.reviews.VendorReview.VendorReviewRepository;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.vendors.Dto.PublicVendorListResponse;
import com.staminal.venue.vendors.Dto.PublicVendorResponse;
import com.staminal.venue.vendors.Entity.VendorCategory;
import com.staminal.venue.vendors.Entity.VendorMedia;
import com.staminal.venue.vendors.Entity.VendorPackage;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorMediaRepository;
import com.staminal.venue.vendors.Repository.VendorPackageRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;
import com.staminal.venue.vendors.Service.PublicVendorService;

@ExtendWith(MockitoExtension.class)
class PublicVendorServiceTest {

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private VendorPackageRepository vendorPackageRepository;

    @Mock
    private VendorMediaRepository vendorMediaRepository;

    @Mock
    private VendorReviewRepository vendorReviewRepository;

    private PublicVendorService publicVendorService;

    @BeforeEach
    void setUp() {
        publicVendorService = new PublicVendorService(
                vendorRepository,
                vendorPackageRepository,
                vendorMediaRepository,
                vendorReviewRepository);
    }

    @Test
    void searchReturnsApprovedVendorsMatchingFrontendLocationAndCategory() {
        Vendors vendor = vendor(501L, VendorStatus.APPROVED);

        when(vendorRepository.findByStatus(VendorStatus.APPROVED)).thenReturn(List.of(vendor));
        when(vendorMediaRepository.findByVendor_Id(501L)).thenReturn(List.of(media(vendor)));
        when(vendorPackageRepository.findByVendor_Id(501L)).thenReturn(List.of(vendorPackage(vendor)));
        when(vendorReviewRepository.findByVendor_IdAndActiveTrueAndModerationStatus(
                501L,
                ReviewModerationStatus.PUBLISHED))
                .thenReturn(List.of(
                        vendorReview(901L, vendor, 5),
                        vendorReview(902L, vendor, 4)));

        PublicVendorListResponse response = publicVendorService.searchPublicVendors(
                "catering",
                "Chennai",
                "Chennai",
                "CATERING",
                new BigDecimal("50000"),
                true,
                "PRICE_ASC",
                0,
                24);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        PublicVendorResponse item = response.content().get(0);
        assertThat(item.id()).isEqualTo("501");
        assertThat(item.category()).isEqualTo("CATERING");
        assertThat(item.imageUrl()).isEqualTo("https://cdn.example.com/vendor-cover.jpg");
        assertThat(item.packages()).extracting("name").containsExactly("Wedding essentials");
        assertThat(item.packages().get(0).includes()).containsExactly("Welcome drink", "Meal service");
        assertThat(item.rating()).isEqualTo(4.5);
        assertThat(item.reviewCount()).isEqualTo(2);
        assertThat(item.completedEvents()).isEqualTo(2);
        assertThat(item.reviews()).extracting("customerName").containsExactly("Priya Raman", "Priya Raman");
        assertThat(item.instagramUrl()).isEqualTo("https://instagram.com/saffronleafcatering");
        assertThat(item.facebookUrl()).isEqualTo("https://facebook.com/saffronleafcatering");
        assertThat(item.whatsAppUrl()).isEqualTo("https://wa.me/919884012345");
    }

    @Test
    void detailSupportsSlugForApprovedVendor() {
        Vendors vendor = vendor(501L, VendorStatus.APPROVED);

        when(vendorRepository.findByStatus(VendorStatus.APPROVED)).thenReturn(List.of(vendor));
        when(vendorMediaRepository.findByVendor_Id(501L)).thenReturn(List.of());
        when(vendorPackageRepository.findByVendor_Id(501L)).thenReturn(List.of());
        when(vendorReviewRepository.findByVendor_IdAndActiveTrueAndModerationStatus(
                501L,
                ReviewModerationStatus.PUBLISHED))
                .thenReturn(List.of());
        PublicVendorResponse response = publicVendorService.getPublicVendor("saffron-leaf-catering");

        assertThat(response.id()).isEqualTo("501");
        assertThat(response.businessName()).isEqualTo("Saffron Leaf Catering");
        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.packages()).hasSize(1);
    }

    @Test
    void detailHidesNonApprovedVendor() {
        Vendors vendor = vendor(501L, VendorStatus.PENDING);

        when(vendorRepository.findById(501L)).thenReturn(Optional.of(vendor));

        assertThatThrownBy(() -> publicVendorService.getPublicVendor("501"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }

    private Vendors vendor(Long id, VendorStatus status) {
        Vendors vendor = new Vendors();
        vendor.setId(id);
        vendor.setVendorName("Manoj Krishnan");
        vendor.setBusinessName("Saffron Leaf Catering");
        vendor.setDescription("Premium wedding catering service");
        vendor.setInstagramUrl("https://instagram.com/saffronleafcatering");
        vendor.setFacebookUrl("https://facebook.com/saffronleafcatering");
        vendor.setWhatsAppUrl("https://wa.me/919884012345");
        vendor.setCity("Chennai");
        vendor.setArea("Adyar");
        vendor.setCoverImageUrl("https://cdn.example.com/vendor.jpg");
        vendor.setStartingPrice(new BigDecimal("45000"));
        vendor.setAverageRating(4.5);
        vendor.setReviewCount(2);
        vendor.setPackageName("Wedding essentials");
        vendor.setPackageDescription("Starter package details");
        vendor.setServices(List.of("Wedding catering", "Reception service"));
        vendor.setCategories(Set.of(category("Catering")));
        vendor.setStatus(status);
        vendor.setUpdatedAt(Instant.parse("2026-06-01T10:00:00Z"));
        return vendor;
    }

    private VendorCategory category(String name) {
        VendorCategory category = new VendorCategory();
        category.setId(8L);
        category.setCategoryName(name);
        return category;
    }

    private VendorMedia media(Vendors vendor) {
        VendorMedia media = new VendorMedia();
        media.setId(701L);
        media.setVendor(vendor);
        media.setMediaUrl("https://cdn.example.com/vendor-cover.jpg");
        media.setIsPrimary(true);
        return media;
    }

    private VendorPackage vendorPackage(Vendors vendor) {
        VendorPackage vendorPackage = new VendorPackage();
        vendorPackage.setId(801L);
        vendorPackage.setVendor(vendor);
        vendorPackage.setPackageName("Wedding essentials");
        vendorPackage.setDescription("Starter package details");
        vendorPackage.setPrice(new BigDecimal("45000"));
        vendorPackage.setIncludes(List.of("Welcome drink", "Meal service"));
        return vendorPackage;
    }

    private VendorReview vendorReview(Long id, Vendors vendor, int rating) {

        User customer = new User();
        customer.setId(101L);
        customer.setFullName("Priya Raman");

        VendorLead lead = new VendorLead();
        lead.setEventType("Wedding");
        lead.setEventDate(LocalDate.parse("2026-08-12"));

        VendorReview review = new VendorReview();

        review.setId(id);
        review.setVendor(vendor);
        review.setCustomer(customer);
        review.setVendorLead(lead);
        review.setRating(rating);
        review.setComment("Excellent service.");
        review.setActive(true);
        review.setModerationStatus(ReviewModerationStatus.PUBLISHED);
        review.setCreatedAt(Instant.parse("2026-06-20T10:00:00Z"));

        return review;
    }
}
