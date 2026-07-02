package com.staminal.venue.reviews;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.bookings.Booking;
import com.staminal.venue.bookings.BookingRepository;
import com.staminal.venue.enquiries.Enquiry;
import com.staminal.venue.enquiries.EnquiryRepository;
import com.staminal.venue.enums.BookingStatus;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.reviews.Dto.CreateReviewRequest;
import com.staminal.venue.reviews.Dto.ReviewEligibilityResponse;
import com.staminal.venue.reviews.Dto.ReviewResponse;
import com.staminal.venue.reviews.Dto.UpdateReviewRequest;
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

    public ReviewResponse createReview(
            CreateReviewRequest request,
            Authentication authentication) {

        User customer = currentUser(authentication);

        Enquiry enquiry = enquiryRepository
                .findByIdAndCustomer_Id(
                        request.enquiryId(),
                        customer.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Enquiry not found"));

        Booking booking = bookingRepository
                .findByEnquiry_Id(request.enquiryId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Booking not found"));

        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Booking is not completed");
        }

        if (reviewRepository.existsByEnquiry_IdAndActiveTrue(request.enquiryId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Review already exists");
        }

        Review review = new Review();

        review.setBooking(booking);
        review.setEnquiry(enquiry);
        review.setHall(booking.getHall());
        review.setCustomer(customer);

        review.setRating(request.rating());
        review.setComment(request.comment());

        review.setVerifiedService(true);
        review.setActive(true);

        reviewRepository.save(review);

        return toResponse(review);
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

        review.setRating(request.rating());
        review.setComment(request.comment());

        reviewRepository.save(review);

        return toResponse(review);
    }

    public ReviewEligibilityResponse getEligibility(
            Long enquiryId,
            Authentication authentication) {

        User customer = currentUser(authentication);

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

        if (reviewRepository.existsByEnquiry_IdAndActiveTrue(enquiryId)) {
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
                        : review.getEnquiry().getId(),

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