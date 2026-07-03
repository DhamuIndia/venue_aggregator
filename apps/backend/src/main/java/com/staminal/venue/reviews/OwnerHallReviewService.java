package com.staminal.venue.reviews;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.bookings.Booking;
import com.staminal.venue.enquiries.Enquiry;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.reviews.Dto.OwnerHallReviewListResponse;
import com.staminal.venue.reviews.Dto.OwnerHallReviewResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OwnerHallReviewService {

    private final HallRepository hallRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public OwnerHallReviewListResponse getHallReviews(Long hallId, Authentication authentication) {
        findOwnedHall(hallId, authentication);

        List<OwnerHallReviewResponse> reviews = reviewRepository.findOwnerPublishedReviewsByHallId(hallId)
                .stream()
                .map(this::toResponse)
                .toList();

        double averageRating = reviews.stream()
                .filter(review -> review.rating() != null)
                .mapToInt(OwnerHallReviewResponse::rating)
                .average()
                .orElse(0);

        return new OwnerHallReviewListResponse(
                reviews,
                reviews.size(),
                Math.round(averageRating * 10.0) / 10.0);
    }

    private Halls findOwnedHall(Long hallId, Authentication authentication) {
        Long userId = currentUserId(authentication);
        Halls hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"));
        if (hall.getOwnerUserId() == null || !hall.getOwnerUserId().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hall does not belong to this owner");
        }
        return hall;
    }

    private OwnerHallReviewResponse toResponse(Review review) {
        return new OwnerHallReviewResponse(
                String.valueOf(review.getId()),
                customerName(review),
                review.getRating(),
                eventType(review),
                eventDate(review),
                review.getComment(),
                review.getVerifiedService());
    }

    private String customerName(Review review) {
        if (review.getCustomer() != null && hasText(review.getCustomer().getFullName())) {
            return review.getCustomer().getFullName();
        }
        Enquiry enquiry = review.getEnquiry();
        if (enquiry != null && hasText(enquiry.getCustomerName())) {
            return enquiry.getCustomerName();
        }
        Booking booking = review.getBooking();
        if (booking != null && hasText(booking.getCustomerName())) {
            return booking.getCustomerName();
        }
        return "Customer";
    }

    private String eventType(Review review) {
        Enquiry enquiry = review.getEnquiry();
        if (enquiry != null && hasText(enquiry.getEventType())) {
            return enquiry.getEventType();
        }
        return "Completed event";
    }

    private String eventDate(Review review) {
        Enquiry enquiry = review.getEnquiry();
        LocalDate enquiryDate = enquiry == null ? null : enquiry.getEventDate();
        if (enquiryDate != null) {
            return enquiryDate.toString();
        }

        Booking booking = review.getBooking();
        LocalDate bookingDate = booking == null ? null : booking.getEventDate();
        if (bookingDate != null) {
            return bookingDate.toString();
        }

        return review.getCreatedAt() == null ? "" : review.getCreatedAt().toString();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session is invalid", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
