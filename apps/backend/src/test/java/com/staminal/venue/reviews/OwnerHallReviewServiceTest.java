package com.staminal.venue.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.enquiries.Enquiry;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.reviews.Dto.OwnerHallReviewListResponse;
import com.staminal.venue.users.Entity.User;

@ExtendWith(MockitoExtension.class)
class OwnerHallReviewServiceTest {

    @Mock
    private HallRepository hallRepository;

    @Mock
    private ReviewRepository reviewRepository;

    private OwnerHallReviewService ownerHallReviewService;

    @BeforeEach
    void setUp() {
        ownerHallReviewService = new OwnerHallReviewService(hallRepository, reviewRepository);
    }

    @Test
    void ownerCanListPublishedReviewsForOwnHall() {
        Halls hall = hall(owner());
        Review review = review(41L, 5);
        Review secondReview = review(42L, 4);

        when(hallRepository.findById(201L)).thenReturn(Optional.of(hall));
        when(reviewRepository.findOwnerPublishedReviewsByHallId(201L)).thenReturn(List.of(review, secondReview));

        OwnerHallReviewListResponse response = ownerHallReviewService.getHallReviews(
                201L,
                auth(301L));

        assertThat(response.totalReviews()).isEqualTo(2);
        assertThat(response.averageRating()).isEqualTo(4.5);
        assertThat(response.reviews()).hasSize(2);
        assertThat(response.reviews().get(0).id()).isEqualTo("41");
        assertThat(response.reviews().get(0).customerName()).isEqualTo("Priya Raman");
        assertThat(response.reviews().get(0).eventType()).isEqualTo("Wedding");
        assertThat(response.reviews().get(0).eventDate()).isEqualTo("2026-08-02");
        assertThat(response.reviews().get(0).verifiedService()).isTrue();
    }

    @Test
    void ownerCannotReadAnotherOwnersHallReviews() {
        Halls hall = hall(otherOwner());

        when(hallRepository.findById(201L)).thenReturn(Optional.of(hall));

        assertThatThrownBy(() -> ownerHallReviewService.getHallReviews(201L, auth(301L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    private Review review(Long id, Integer rating) {
        Review review = new Review();
        review.setId(id);
        review.setCustomer(customer());
        review.setEnquiry(enquiry());
        review.setRating(rating);
        review.setComment("Clean venue, excellent dining setup, and quick communication.");
        review.setVerifiedService(true);
        review.setActive(true);
        review.setModerationStatus(ReviewModerationStatus.PUBLISHED);
        review.setCreatedAt(Instant.parse("2026-06-25T10:30:00Z"));
        return review;
    }

    private Enquiry enquiry() {
        Enquiry enquiry = new Enquiry();
        enquiry.setEventType("Wedding");
        enquiry.setEventDate(LocalDate.parse("2026-08-02"));
        enquiry.setCustomerName("Priya Raman");
        return enquiry;
    }

    private Halls hall(User owner) {
        Halls hall = new Halls();
        hall.setId(201L);
        hall.setName("Emerald Convention Centre");
        hall.setOwnerUserId(owner);
        return hall;
    }

    private User owner() {
        User user = new User();
        user.setId(301L);
        user.setFullName("Arun Kumar");
        return user;
    }

    private User otherOwner() {
        User user = new User();
        user.setId(302L);
        user.setFullName("Other Owner");
        return user;
    }

    private User customer() {
        User user = new User();
        user.setId(101L);
        user.setFullName("Priya Raman");
        return user;
    }

    private UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_HALL_OWNER")));
    }
}
