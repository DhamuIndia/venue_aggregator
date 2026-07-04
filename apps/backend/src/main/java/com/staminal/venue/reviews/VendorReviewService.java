package com.staminal.venue.reviews;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.enums.UserRole;
import com.staminal.venue.vendors.Dto.PublicVendorReviewResponse;
import com.staminal.venue.vendors.Dto.VendorReviewListResponse;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorReviewService {

    private final ReviewRepository reviewRepository;
    private final VendorRepository vendorRepository;

    @Transactional(readOnly = true)
    public VendorReviewListResponse getMyReviews(Authentication authentication) {
        Vendors vendor = currentVendor(authentication);
        List<Review> publishedReviews = reviewRepository.findPublishedReviewsByVendorId(vendor.getId());
        List<PublicVendorReviewResponse> reviews = publishedReviews.stream()
                .map(this::toResponse)
                .toList();

        return new VendorReviewListResponse(
                reviews,
                reviews.size(),
                averageRating(publishedReviews));
    }

    private Vendors currentVendor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        if (!hasRole(authentication, UserRole.VENDOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "VENDOR role is required");
        }

        Long userId;
        try {
            userId = Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user session");
        }

        return vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor profile not found"));
    }

    private boolean hasRole(Authentication authentication, UserRole role) {
        String authority = "ROLE_" + role.name();
        return authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private PublicVendorReviewResponse toResponse(Review review) {
        return new PublicVendorReviewResponse(
                String.valueOf(review.getId()),
                review.getCustomer() == null || !hasText(review.getCustomer().getFullName())
                        ? "Customer"
                        : review.getCustomer().getFullName(),
                review.getRating() == null ? 0 : review.getRating(),
                eventType(review),
                firstText(review.getComment(), ""),
                eventDate(review),
                Boolean.TRUE.equals(review.getVerifiedService()));
    }

    private double averageRating(List<Review> reviews) {
        double average = reviews.stream()
                .filter(review -> review.getRating() != null)
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);
        return Math.round(average * 10.0) / 10.0;
    }

    private String eventType(Review review) {
        if (review.getEnquiry() != null && hasText(review.getEnquiry().getEventType())) {
            return review.getEnquiry().getEventType();
        }
        return "Completed event";
    }

    private String eventDate(Review review) {
        if (review.getEnquiry() != null && review.getEnquiry().getEventDate() != null) {
            return review.getEnquiry().getEventDate().toString();
        }
        if (review.getBooking() != null && review.getBooking().getEventDate() != null) {
            return review.getBooking().getEventDate().toString();
        }
        return review.getCreatedAt() == null ? "" : review.getCreatedAt().toString();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
