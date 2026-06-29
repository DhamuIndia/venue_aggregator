package com.staminal.venue.availability;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.staminal.venue.availability.Dto.AvailabilityResponse;
import com.staminal.venue.availability.Dto.BlockedDateResponse;
import com.staminal.venue.availability.Dto.BookingAvailabilityResponse;
import com.staminal.venue.bookings.Booking;
import com.staminal.venue.bookings.BookingRepository;
import com.staminal.venue.enums.BookingStatus;
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

                List<Booking> bookings = bookingRepository.findByHall_IdAndStatus(
                                hall.getId(),
                                BookingStatus.CONFIRMED);

                AvailabilityResponse response = new AvailabilityResponse();

                response.setBlockedDates(
                                blockedDates.stream()
                                                .map(this::mapBlockedDate)
                                                .collect(Collectors.toList()));

                response.setBookings(
                                bookings.stream()
                                                .map(this::mapBooking)
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

        private Halls getOwnerHall(Long hallId, Authentication authentication) {

                Long userId = Long.valueOf(authentication.getName());

                User owner = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("Owner not found"));

                return hallRepository.findById(hallId)
                                .filter(hall -> hall.getOwnerUserId().getId().equals(owner.getId()))
                                .orElseThrow(() -> new RuntimeException("Hall not found"));
        }
}