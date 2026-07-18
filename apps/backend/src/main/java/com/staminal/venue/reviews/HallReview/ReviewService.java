package com.staminal.venue.reviews.HallReview;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.bookings.Booking;
import com.staminal.venue.bookings.BookingRepository;
import com.staminal.venue.enquiries.Enquiry;
import com.staminal.venue.enquiries.EnquiryIds;
import com.staminal.venue.enquiries.EnquiryRepository;
import com.staminal.venue.enums.BookingStatus;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.reviews.HallReview.Dto.CreateReviewRequest;
import com.staminal.venue.reviews.HallReview.Dto.ReviewEligibilityResponse;
import com.staminal.venue.reviews.HallReview.Dto.ReviewResponse;
import com.staminal.venue.reviews.HallReview.Dto.UpdateReviewRequest;
import com.staminal.venue.reviews.VendorReview.VendorRatingAggregateService;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class ReviewService {

        private final ReviewRepository reviewRepository;
        private final BookingRepository bookingRepository;
        private final EnquiryRepository enquiryRepository;
        private final UserRepository userRepository;
        private final AuditService auditService;
        // private final VendorRatingAggregateService vendorRatingAggregateService;

        public ReviewResponse createReview(
                        CreateReviewRequest request,
                        Authentication authentication) {

                User customer = currentUser(authentication);
                Long enquiryId = EnquiryIds.parse(request.enquiryId());

                Enquiry enquiry = enquiryRepository
                                .findByIdAndCustomer_Id(
                                                enquiryId,
                                                customer.getId())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Enquiry not found"));

                Booking booking = bookingRepository
                                .findByEnquiry_Id(enquiryId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Booking not found"));

                if (booking.getStatus() != BookingStatus.COMPLETED) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Booking is not completed");
                }

                if (reviewRepository.existsByEnquiry_Id(enquiryId)) {
                        throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Review already exists");
                }

                Review review = new Review();

                review.setBooking(booking);
                review.setEnquiry(enquiry);
                review.setHall(booking.getHall());
                review.setVendor(enquiry.getVendor());
                review.setCustomer(customer);

                review.setRating(request.rating());
                review.setComment(request.comment());

                review.setVerifiedService(true);
                review.setActive(true);
                review.setModerationStatus(ReviewModerationStatus.PENDING);

                Review savedReview = reviewRepository.save(review);
                // vendorRatingAggregateService.refreshFor(savedReview);

                auditService.record(
                                new AuditCommand(
                                                customer.getId(),
                                                "CUSTOMER",
                                                AuditAction.REVIEW_CREATED,
                                                "REVIEW",
                                                String.valueOf(savedReview.getId()),
                                                "Customer created review",
                                                null,
                                                Map.of(
                                                                "rating", savedReview.getRating(),
                                                                "comment", savedReview.getComment()),
                                                null));

                return toResponse(savedReview);
        }

        public ReviewResponse updateReview(
                        Long reviewId,
                        UpdateReviewRequest request,
                        Authentication authentication) {

                User customer = currentUser(authentication);

                Review review = reviewRepository.findById(reviewId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Review not found"));

                if (!review.getCustomer().getId().equals(customer.getId())) {

                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "Review does not belong to this customer");
                }

                Integer oldRating = review.getRating();
                String oldComment = review.getComment();

                review.setRating(request.rating());
                review.setComment(request.comment());
                review.setModerationStatus(ReviewModerationStatus.PENDING);
                review.setActive(true);
                review.setModerationReason(null);
                review.setModeratedByAdmin(null);
                review.setModeratedAt(null);

                Review savedReview = reviewRepository.save(review);
                // vendorRatingAggregateService.refreshFor(savedReview);

                auditService.record(
                                new AuditCommand(
                                                customer.getId(),
                                                "CUSTOMER",
                                                AuditAction.REVIEW_UPDATED,
                                                "REVIEW",
                                                String.valueOf(savedReview.getId()),
                                                "Customer updated review",
                                                Map.of(
                                                                "rating", oldRating,
                                                                "comment", oldComment),
                                                Map.of(
                                                                "rating", savedReview.getRating(),
                                                                "comment", savedReview.getComment()),
                                                null));

                return toResponse(savedReview);
        }

        public ReviewEligibilityResponse getEligibility(
                        String requestedEnquiryId,
                        Authentication authentication) {

                User customer = currentUser(authentication);
                Long enquiryId = EnquiryIds.parse(requestedEnquiryId);

                enquiryRepository
                                .findByIdAndCustomer_Id(enquiryId, customer.getId())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Enquiry not found"));

                Booking booking = bookingRepository
                                .findByEnquiry_Id(enquiryId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Booking not found"));

                if (booking.getStatus() != BookingStatus.COMPLETED) {
                        return new ReviewEligibilityResponse(
                                        false,
                                        "Booking is not completed");
                }

                if (reviewRepository.existsByEnquiry_Id(enquiryId)) {
                        return new ReviewEligibilityResponse(
                                        false,
                                        "Review already submitted");
                }

                return new ReviewEligibilityResponse(
                                true,
                                "Eligible");
        }

        private User currentUser(Authentication authentication) {

                if (authentication == null || !authentication.isAuthenticated()) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "Authentication required");
                }

                if (!hasRole(authentication, UserRole.CUSTOMER)) {
                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "CUSTOMER role is required");
                }

                Long userId;

                try {
                        userId = Long.valueOf(authentication.getName());
                } catch (NumberFormatException exception) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "Invalid user session");
                }

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED,
                                                "User not found"));

                if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "User account is not active");
                }

                return user;
        }

        private boolean hasRole(
                        Authentication authentication,
                        UserRole role) {

                String authority = "ROLE_" + role.name();

                return authentication.getAuthorities()
                                .stream()
                                .map(GrantedAuthority::getAuthority)
                                .anyMatch(authority::equals);
        }

        private ReviewResponse toResponse(Review review) {

                return new ReviewResponse(

                                review.getId(),

                                review.getBooking() == null
                                                ? null
                                                : review.getBooking().getId(),

                                review.getEnquiry() == null
                                                ? null
                                                : EnquiryIds.format(review.getEnquiry().getId()),

                                review.getHall() == null
                                                ? null
                                                : review.getHall().getId(),

                                review.getHall() == null
                                                ? null
                                                : review.getHall().getName(),

                                review.getRating(),

                                review.getComment(),

                                review.getVerifiedService(),

                                review.getCreatedAt(),

                                review.getUpdatedAt());
        }

}
