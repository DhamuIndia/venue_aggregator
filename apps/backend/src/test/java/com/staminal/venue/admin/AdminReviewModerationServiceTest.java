package com.staminal.venue.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.reviews.Review;
import com.staminal.venue.reviews.ReviewModerationStatus;
import com.staminal.venue.reviews.ReviewRepository;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminReviewModerationServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    private AdminReviewModerationService adminReviewModerationService;

    @BeforeEach
    void setUp() {
        adminReviewModerationService = new AdminReviewModerationService(
                reviewRepository,
                adminRepository,
                userRepository,
                auditService);
    }

    @Test
    void reportedFilterReturnsModerationQueue() {
        Review review = review(240L, ReviewModerationStatus.PENDING);

        when(reviewRepository.findForAdminByStatuses(any())).thenReturn(List.of(review));

        AdminReviewListResponse response = adminReviewModerationService.getReviews("REPORTED", 0, 50);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).id()).isEqualTo("240");
        assertThat(response.content().get(0).hallName()).isEqualTo("Lotus Heritage Hall");
        assertThat(response.content().get(0).customerName()).isEqualTo("Priya Raman");
        assertThat(response.content().get(0).status()).isEqualTo("PENDING");
    }

    @Test
    void publishReviewStoresModeratorAndAuditTrail() {
        Review review = review(240L, ReviewModerationStatus.PENDING);
        User adminUser = adminUser();
        Admin legacyAdmin = legacyAdmin();

        when(reviewRepository.findByIdForAdmin(240L)).thenReturn(Optional.of(review));
        when(userRepository.findById(900L)).thenReturn(Optional.of(adminUser));
        when(adminRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(legacyAdmin));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminReviewResponse response = adminReviewModerationService.moderateReview(
                "240",
                new AdminReviewModerationRequest(ReviewModerationStatus.PUBLISHED, "Verified completed booking"),
                auth());

        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(reviewCaptor.capture());

        Review saved = reviewCaptor.getValue();
        assertThat(saved.getModerationStatus()).isEqualTo(ReviewModerationStatus.PUBLISHED);
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getModeratedByAdmin()).isSameAs(legacyAdmin);
        assertThat(saved.getModeratedAt()).isNotNull();
        verify(auditService).record(any());

        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(response.moderatedBy()).isEqualTo(901L);
    }

    @Test
    void hideReviewRequiresReason() {
        Review review = review(240L, ReviewModerationStatus.REPORTED);

        when(reviewRepository.findByIdForAdmin(240L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> adminReviewModerationService.moderateReview(
                "240",
                new AdminReviewModerationRequest(ReviewModerationStatus.HIDDEN, " "),
                auth()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    private Review review(Long id, ReviewModerationStatus status) {
        Review review = new Review();
        review.setId(id);
        review.setHall(hall());
        review.setCustomer(customer());
        review.setRating(4);
        review.setComment("The hall team handled the event very well.");
        review.setReportReason("Pending moderation");
        review.setVerifiedService(true);
        review.setActive(true);
        review.setModerationStatus(status);
        review.setCreatedAt(Instant.parse("2026-06-25T10:30:00Z"));
        return review;
    }

    private Halls hall() {
        Halls hall = new Halls();
        hall.setId(201L);
        hall.setName("Lotus Heritage Hall");
        return hall;
    }

    private User customer() {
        User user = new User();
        user.setId(101L);
        user.setFullName("Priya Raman");
        return user;
    }

    private User adminUser() {
        User user = new User();
        user.setId(900L);
        user.setFullName("Admin User");
        user.setEmail("admin@example.com");
        return user;
    }

    private Admin legacyAdmin() {
        Admin admin = new Admin();
        admin.setId(901L);
        admin.setFullName("Legacy Admin");
        admin.setEmail("admin@example.com");
        return admin;
    }

    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(
                "900",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
