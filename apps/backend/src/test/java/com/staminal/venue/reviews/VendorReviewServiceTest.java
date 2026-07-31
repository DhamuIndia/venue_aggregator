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

import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.leads.VendorLeadRepository;
import com.staminal.venue.reviews.VendorReview.VendorReview;
import com.staminal.venue.reviews.VendorReview.VendorReviewRepository;
import com.staminal.venue.reviews.VendorReview.VendorReviewService;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Dto.VendorReviewListResponse;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

@ExtendWith(MockitoExtension.class)
class VendorReviewServiceTest {

    @Mock
    private VendorRepository vendorRepository;

    @Mock
    private VendorReviewRepository vendorReviewRepository;

    private VendorReviewService vendorReviewService;

    @Mock
    private VendorLeadRepository vendorLeadRepository;

    @Mock
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        vendorReviewService = new VendorReviewService(
                vendorRepository,
                vendorReviewRepository,
                vendorLeadRepository,
                userRepository);
    }

    @Test
    void getMyReviewsReturnsPublishedVendorReviewSummary() {
        Vendors vendor = vendor();

        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(vendorReviewRepository.findByVendor_IdAndActiveTrue(501L))
                .thenReturn(List.of(review(901L, 5), review(902L, 4)));

        VendorReviewListResponse response = vendorReviewService.getMyReviews(vendorAuth());

        assertThat(response.reviewCount()).isEqualTo(2);
        assertThat(response.averageRating()).isEqualTo(4.5);
        assertThat(response.reviews()).extracting("eventType").containsExactly("Wedding", "Wedding");
    }

    private VendorReview review(Long id, int rating) {

        User customer = new User();
        customer.setId(101L);
        customer.setFullName("Priya Raman");

        VendorLead lead = new VendorLead();
        lead.setEventType("Wedding");
        lead.setEventDate(LocalDate.parse("2026-08-12"));

        VendorReview review = new VendorReview();
        review.setId(id);
        review.setCustomer(customer);
        review.setVendorLead(lead);
        review.setRating(rating);
        review.setComment("Excellent service.");
        review.setActive(true);
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
