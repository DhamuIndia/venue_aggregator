package com.staminal.venue.reviews;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.bookings.BookingRepository;
import com.staminal.venue.enquiries.Enquiry;
import com.staminal.venue.enquiries.EnquiryRepository;
import com.staminal.venue.reviews.dto.ReviewEligibilityResponse;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.bookings.Booking;
import com.staminal.venue.enums.BookingStatus;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final EnquiryRepository enquiryRepository;
    private final UserRepository userRepository;

    public ReviewEligibilityResponse checkEligibility(
            @NonNull Long enquiryId,
            Authentication authentication) {

        Enquiry enquiry = enquiryRepository.findById(enquiryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Enquiry not found"));

        Long userId = Long.valueOf(authentication.getName());

        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"));

  Booking booking = bookingRepository.findByEnquiry_Id(enquiryId)
        .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Booking not found"));

if (!booking.getCustomer().getId().equals(customer.getId())) {
    return new ReviewEligibilityResponse(
            false,
            "This enquiry does not belong to you");
}

if (booking.getStatus() != BookingStatus.COMPLETED) {
    return new ReviewEligibilityResponse(
            false,
            "Only completed bookings are eligible");
}

if (reviewRepository.existsByEnquiry_IdAndActiveTrue(enquiryId)) {
    return new ReviewEligibilityResponse(
            false,
            "Review already exists");
}

return new ReviewEligibilityResponse(
        true,
        null);

       
    }
}