package com.staminal.venue.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.staminal.venue.enquiries.Enquiry;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.vendors.Dto.VendorReviewListResponse;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

@ExtendWith(MockitoExtension.class)
class VendorReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private VendorRepository vendorRepository;

    private VendorReviewService vendorReviewService;

    @BeforeEach
    void setUp() {
        vendorReviewService = new VendorReviewService(reviewRepository, vendorRepository);
    }

    @Test
    void getMyReviewsReturnsPublishedVendorReviewSummary() {
        Vendors vendor = vendor();

        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(reviewRepository.findPublishedReviewsByVendorId(501L))
                .thenReturn(List.of(review(901L, 5), review(902L, 4)));

        VendorReviewListResponse response = vendorReviewService.getMyReviews(vendorAuth());

        assertThat(response.reviewCount()).isEqualTo(2);
        assertThat(response.averageRating()).isEqualTo(4.5);
        assertThat(response.reviews()).extracting("eventType").containsExactly("Wedding", "Wedding");
    }

    private Review review(Long id, int rating) {
        User customer = new User();
        customer.setId(101L);
        customer.setFullName("Priya Raman");

        Enquiry enquiry = new Enquiry();
        enquiry.setEventType("Wedding");
        enquiry.setEventDate(LocalDate.parse("2026-08-12"));

        Review review = new Review();
        review.setId(id);
        review.setCustomer(customer);
        review.setEnquiry(enquiry);
        review.setRating(rating);
        review.setComment("Excellent service.");
        review.setVerifiedService(true);
        review.setCreatedAt(Instant.parse("2026-06-20T10:00:00Z"));
        return review;
    }

    private Vendors vendor() {
        Vendors vendor = new Vendors();
        vendor.setId(501L);
        vendor.setBusinessName("Saffron Leaf Catering");
        return vendor;
    }

    private Authentication vendorAuth() {
        return new UsernamePasswordAuthenticationToken(
                "301",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_VENDOR")));
    }
}
