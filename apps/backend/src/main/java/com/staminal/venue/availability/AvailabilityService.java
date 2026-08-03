package com.staminal.venue.availability;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.availability.Dto.AvailabilityResponse;
import com.staminal.venue.availability.Dto.AvailabilitySummary;
import com.staminal.venue.availability.Dto.BookingAvailabilityResponse;
import com.staminal.venue.bookings.Booking;
import com.staminal.venue.bookings.BookingRepository;
import com.staminal.venue.enquiries.EnquirySlotRequest;
import com.staminal.venue.enums.BookingStatus;
import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.halls.Dto.BlockedDateResponse;
import com.staminal.venue.halls.Entity.HallBlockedDate;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallBlockedDateRepository;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

@Service
public class AvailabilityService {

        @Autowired
        private BookingRepository bookingRepository;

        @Autowired
        private HallBlockedDateRepository hallBlockedDateRepository;

        @Autowired
        private HallRepository hallRepository;

        @Autowired
        private UserRepository userRepository;

        public AvailabilityResponse getAvailability(Long hallId, Authentication authentication) {

                Halls hall = getOwnerHall(hallId, authentication);

                List<HallBlockedDate> blockedDates = hallBlockedDateRepository.findByHallId_Id(hall.getId());

                List<Booking> bookings = bookingRepository.findByHallIdAndStatusWithEnquiry(
                                hall.getId(),
                                BookingStatus.CONFIRMED);

                AvailabilityResponse response = new AvailabilityResponse();

                response.setBlockedDates(
                                blockedDates.stream()
                                                .map(this::mapBlockedDate)
                                                .collect(Collectors.toList()));

                response.setBookings(
                                bookings.stream()
                                                .flatMap(booking -> mapBookingSlots(booking, false).stream())
                                                .collect(Collectors.toList()));

                return response;
        }

        public AvailabilityResponse getPublicAvailability(String hallId) {

                Halls hall = getApprovedHall(hallId);

                List<HallBlockedDate> blockedDates = hallBlockedDateRepository.findByHallId_Id(hall.getId());

                List<Booking> bookings = bookingRepository.findByHallIdAndStatusWithEnquiry(
                                hall.getId(),
                                BookingStatus.CONFIRMED);

                AvailabilityResponse response = new AvailabilityResponse();

                response.setBlockedDates(
                                blockedDates.stream()
                                                .map(this::mapPublicBlockedDate)
                                                .collect(Collectors.toList()));

                response.setBookings(
                                bookings.stream()
                                                .flatMap(booking -> mapBookingSlots(booking, true).stream())
                                                .collect(Collectors.toList()));

                return response;
        }

        private BlockedDateResponse mapBlockedDate(HallBlockedDate blockedDate) {

                BlockedDateResponse response = new BlockedDateResponse();

                response.setId(blockedDate.getId());
                response.setDate(blockedDate.getEventDate());
                response.setSlot(blockedDate.getSlotType().name());
                response.setReason(blockedDate.getReason());

                return response;
        }

        private BlockedDateResponse mapPublicBlockedDate(HallBlockedDate blockedDate) {

                BlockedDateResponse response = mapBlockedDate(blockedDate);
                response.setHallId(null);
                response.setReason("Unavailable");

                return response;
        }

        private BookingAvailabilityResponse mapBooking(Booking booking) {

                BookingAvailabilityResponse response = new BookingAvailabilityResponse();

                response.setId(booking.getId());

                if (booking.getEnquiry() != null) {

                        response.setEnquiryId(booking.getEnquiry().getId());

                        response.setEventType(
                                        booking.getEnquiry().getEventType());

                        response.setGuestCount(
                                        booking.getEnquiry().getGuestCount());

                }

                response.setEventDate(booking.getEventDate());

                if (booking.getSlotType() != null) {
                        response.setSlot(booking.getSlotType().name());
                }

                response.setCustomerName(booking.getCustomerName());

                return response;
        }

        private BookingAvailabilityResponse mapPublicBooking(Booking booking) {

                BookingAvailabilityResponse response = new BookingAvailabilityResponse();

                response.setEventDate(booking.getEventDate());
                response.setSlot(booking.getSlotType() != null ? booking.getSlotType().name() : null);
                response.setEventType("Booked event");
                response.setGuestCount(null);
                response.setCustomerName(null);
                response.setEnquiryId(null);
                response.setId(null);

                return response;
        }

        private List<BookingAvailabilityResponse> mapBookingSlots(
                        Booking booking,
                        boolean publicView) {

                if (booking.getEnquiry() == null
                                || booking.getEnquiry().getSlotRequests() == null
                                || booking.getEnquiry().getSlotRequests().isEmpty()) {
                        return List.of(publicView ? mapPublicBooking(booking) : mapBooking(booking));
                }

                return booking.getEnquiry().getSlotRequests()
                                .stream()
                                .map(slotRequest -> mapBookingSlot(booking, slotRequest, publicView))
                                .collect(Collectors.toList());
        }

        private BookingAvailabilityResponse mapBookingSlot(
                        Booking booking,
                        EnquirySlotRequest slotRequest,
                        boolean publicView) {

                BookingAvailabilityResponse response = publicView
                                ? mapPublicBooking(booking)
                                : mapBooking(booking);

                response.setEventDate(slotRequest.getEventDate());
                response.setSlot(slotRequest.getSlotType() != null ? slotRequest.getSlotType().name() : null);
                if (!publicView && slotRequest.getEventType() != null && !slotRequest.getEventType().isBlank()) {
                        response.setEventType(slotRequest.getEventType());
                }

                return response;
        }

        private Halls getOwnerHall(Long hallId, Authentication authentication) {

                if (authentication == null || !authentication.isAuthenticated()) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "Authentication required");
                }

                Long userId;

                try {
                        userId = Long.valueOf(authentication.getName());
                } catch (NumberFormatException exception) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "User session is invalid",
                                        exception);
                }

                User owner = userRepository.findById(userId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED,
                                                "User not found"));

                return hallRepository.findById(hallId)
                                .filter(hall -> hall.getOwnerUserId() != null
                                                && hall.getOwnerUserId().getId().equals(owner.getId()))
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.FORBIDDEN,
                                                "Hall does not belong to this owner"));
        }

        private Halls getApprovedHall(String hallId) {

                Long numericId = tryParseLong(hallId);

                Halls hall = numericId != null
                                ? hallRepository.findById(numericId)
                                                .orElseThrow(() -> new ResponseStatusException(
                                                                HttpStatus.NOT_FOUND,
                                                                "Hall not found"))
                                : hallRepository.findByStatus(HallStatus.APPROVED)
                                                .stream()
                                                .filter(candidate -> slugify(candidate.getName()).equals(slugify(hallId)))
                                                .findFirst()
                                                .orElseThrow(() -> new ResponseStatusException(
                                                                HttpStatus.NOT_FOUND,
                                                                "Hall not found"));

                if (hall.getStatus() != HallStatus.APPROVED) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found");
                }

                return hall;
        }

        public AvailabilitySummary getAvailabilitySummary(Long hallId) {

                AvailabilitySummary summary = new AvailabilitySummary();

                summary.setAvailableDays(25);
                summary.setBookedDays(5);
                summary.setTotalDays(30);

                return summary;
        }

        private Long tryParseLong(String value) {
                try {
                        return Long.valueOf(value.trim());
                } catch (RuntimeException exception) {
                        return null;
                }
        }

        private String slugify(String value) {
                return normalize(value).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        }

        private String normalize(String value) {
                return value == null
                                ? ""
                                : value.trim().toLowerCase(java.util.Locale.ROOT);
        }
}
