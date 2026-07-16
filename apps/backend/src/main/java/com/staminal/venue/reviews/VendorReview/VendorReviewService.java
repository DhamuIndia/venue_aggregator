package com.staminal.venue.reviews.VendorReview;

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
import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.enums.VendorLeadStatus;
import com.staminal.venue.leads.VendorLeadRepository;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.reviews.HallReview.ReviewModerationStatus;
import com.staminal.venue.reviews.VendorReview.CreateVendorReviewRequest;
import com.staminal.venue.reviews.VendorReview.VendorReviewResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorReviewService {

        private final VendorRepository vendorRepository;
        private final VendorReviewRepository vendorReviewRepository;
        private final VendorLeadRepository vendorLeadRepository;
        private final UserRepository userRepository;
        private final AuditService auditService;
        private final VendorRatingAggregateService vendorRatingAggregateService;

        @Transactional(readOnly = true)
        public VendorReviewListResponse getMyReviews(Authentication authentication) {
                Vendors vendor = currentVendor(authentication);
                List<VendorReview> reviews = vendorReviewRepository.findByVendor_IdAndActiveTrue(vendor.getId());
                List<PublicVendorReviewResponse> reviewResponses = reviews.stream()
                                .map(this::toResponse)
                                .toList();

                return new VendorReviewListResponse(
                                reviewResponses,
                                reviews.size(),
                                averageRating(reviews));
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
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Vendor profile not found"));
        }

        private boolean hasRole(Authentication authentication, UserRole role) {
                String authority = "ROLE_" + role.name();
                return authentication.getAuthorities()
                                .stream()
                                .map(GrantedAuthority::getAuthority)
                                .anyMatch(authority::equals);
        }

        private PublicVendorReviewResponse toResponse(VendorReview review) {

                return new PublicVendorReviewResponse(
                                String.valueOf(review.getId()),
                                review.getCustomer() == null
                                                ? "Customer"
                                                : review.getCustomer().getFullName(),
                                review.getRating(),
                                review.getVendorLead().getEventType(),
                                review.getComment(),
                                review.getVendorLead().getEventDate().toString(),
                                true);
        }

        private double averageRating(List<VendorReview> reviews) {
                double average = reviews.stream()
                                .filter(review -> review.getRating() != null)
                                .mapToInt(VendorReview::getRating)
                                .average()
                                .orElse(0.0);
                return Math.round(average * 10.0) / 10.0;
        }

        @Transactional
        public VendorReviewResponse createVendorReview(
                        CreateVendorReviewRequest request,
                        Authentication authentication) {

                Long userId = Long.valueOf(authentication.getName());

                User customer = userRepository.findById(userId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Customer not found"));

                Long leadId = Long.parseLong(
                                request.leadId().replace("VLEAD-", ""));
                VendorLead lead = vendorLeadRepository.findById(leadId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Lead not found"));

                if (!lead.getCustomer().getId().equals(customer.getId())) {
                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "You cannot review this lead");
                }

                if (lead.getStatus() != VendorLeadStatus.COMPLETED) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Lead is not completed");
                }

                if (vendorReviewRepository.existsByVendorLead_Id(lead.getId())) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Review already exists");
                }

                VendorReview review = new VendorReview();

                review.setVendorLead(lead);
                review.setVendor(lead.getVendor());
                review.setCustomer(customer);

                review.setRating(request.rating());
                review.setComment(request.comment());

                review.setActive(true);
                review.setModerationStatus(ReviewModerationStatus.PENDING);
                review = vendorReviewRepository.save(review);

                // vendorRatingAggregateService.refreshForVendorId(
                //                 lead.getVendor().getId());

                return new VendorReviewResponse(
                                review.getId(),
                                lead.getId(),
                                lead.getVendor().getId(),
                                lead.getVendor().getBusinessName(),
                                review.getRating(),
                                review.getComment(),
                                review.getCreatedAt());

        }

        @Transactional(readOnly = true)
        public VendorReviewEligibilityResponse getReviewEligibility(
                        String leadId,
                        Authentication authentication) {

                Long id = Long.valueOf(leadId.replace("VLEAD-", ""));
                Long userId = Long.valueOf(authentication.getName());

                User customer = userRepository.findById(userId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Customer not found"));

                VendorLead lead = vendorLeadRepository.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Lead not found"));

                if (!lead.getCustomer().getId().equals(customer.getId())) {
                        return new VendorReviewEligibilityResponse(
                                        false,
                                        leadId,
                                        lead.getVendor().getBusinessName(),
                                        lead.getEventDate().toString(),
                                        lead.getEventType(),
                                        "You cannot review this lead");
                }

                if (lead.getStatus() != VendorLeadStatus.COMPLETED) {
                        return new VendorReviewEligibilityResponse(
                                        false,
                                        leadId,
                                        lead.getVendor().getBusinessName(),
                                        lead.getEventDate().toString(),
                                        lead.getEventType(),
                                        "Lead is not completed");
                }

                if (vendorReviewRepository.existsByVendorLead_Id(id)) {
                        return new VendorReviewEligibilityResponse(
                                        false,
                                        leadId,
                                        lead.getVendor().getBusinessName(),
                                        lead.getEventDate().toString(),
                                        lead.getEventType(),
                                        "Review already exists");
                }

                return new VendorReviewEligibilityResponse(
                                true,
                                leadId,
                                lead.getVendor().getBusinessName(),
                                lead.getEventDate().toString(),
                                lead.getEventType(),
                                null);
        }
}
