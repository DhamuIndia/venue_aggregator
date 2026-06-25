package com.staminal.venue.bookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

import com.staminal.venue.bookings.dto.BookingListResponse;
import com.staminal.venue.bookings.dto.BookingResponse;
import com.staminal.venue.bookings.dto.UpdateBookingStatusRequest;
import com.staminal.venue.enquiries.Enquiry;
import com.staminal.venue.enums.BookingStatus;
import com.staminal.venue.enums.EnquiryStatus;
import com.staminal.venue.enums.PaymentStatus;
import com.staminal.venue.enums.SlotType;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Hall.VendorHallDetails;
import com.staminal.venue.vendors.Hall.VendorHallRepository;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private VendorHallRepository vendorHallRepository;

    @Mock
    private UserRepository userRepository;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository, vendorHallRepository, userRepository);
    }

    @Test
    void customerCanListOwnBookings() {
        Booking booking = booking();

        when(userRepository.findById(101L)).thenReturn(Optional.of(customer()));
        when(bookingRepository.findByCustomer_IdOrderByEventDateDesc(101L)).thenReturn(List.of(booking));

        BookingListResponse response = bookingService.getCustomerBookings(auth(101L, UserRole.CUSTOMER));

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).id()).isEqualTo("BOOK-000088");
        assertThat(response.items().get(0).hallId()).isEqualTo("emerald-convention-centre");
        assertThat(response.items().get(0).paymentStatus()).isEqualTo(PaymentStatus.ADVANCE_PENDING);
    }

    @Test
    void customerCannotReadAnotherCustomersBooking() {
        Booking booking = booking();
        booking.setCustomer(otherCustomer());

        when(userRepository.findById(101L)).thenReturn(Optional.of(customer()));
        when(bookingRepository.findById(88L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.getCustomerBooking("BOOK-000088", auth(101L, UserRole.CUSTOMER)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    void ownerCanCompleteConfirmedBooking() {
        Booking booking = booking();

        when(userRepository.findById(301L)).thenReturn(Optional.of(owner()));
        when(bookingRepository.findById(88L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.updateOwnerBookingStatus(
                "BOOK-000088",
                new UpdateBookingStatusRequest(BookingStatus.COMPLETED, null),
                auth(301L, UserRole.HALL_OWNER));

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(bookingCaptor.capture());

        assertThat(response.status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(bookingCaptor.getValue().getCompletedAt()).isNotNull();
        assertThat(bookingCaptor.getValue().getEnquiry().getStatus()).isEqualTo(EnquiryStatus.COMPLETED);
    }

    @Test
    void completedBookingCannotBeCancelled() {
        Booking booking = booking();
        booking.setStatus(BookingStatus.COMPLETED);

        when(userRepository.findById(301L)).thenReturn(Optional.of(owner()));
        when(bookingRepository.findById(88L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.updateOwnerBookingStatus(
                "88",
                new UpdateBookingStatusRequest(BookingStatus.CANCELLED, "Customer requested cancellation."),
                auth(301L, UserRole.HALL_OWNER)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    private Booking booking() {
        Booking booking = new Booking();
        booking.setId(88L);
        booking.setHall(hall());
        booking.setEnquiry(enquiry());
        booking.setCustomer(customer());
        booking.setEventDate(LocalDate.now().plusDays(15));
        booking.setSlotType(SlotType.EVENING);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setAmount(new BigDecimal("125000.00"));
        booking.setPaymentStatus(PaymentStatus.ADVANCE_PENDING);
        booking.setConfirmedAt(Instant.parse("2026-06-25T10:30:00Z"));
        booking.setCustomerName("Priya Raman");
        booking.setCustomerPhone("9876543210");
        booking.setCustomerEmail("priya@example.com");
        booking.setCreatedAt(Instant.parse("2026-06-25T10:30:00Z"));
        booking.setUpdatedAt(Instant.parse("2026-06-25T10:30:00Z"));
        return booking;
    }

    private Enquiry enquiry() {
        Enquiry enquiry = new Enquiry();
        enquiry.setId(55L);
        enquiry.setStatus(EnquiryStatus.CONFIRMED);
        enquiry.setEventType("Wedding");
        enquiry.setGuestCount(450);
        enquiry.setMessage("Please share catering options.");
        return enquiry;
    }

    private VendorHallDetails hall() {
        Vendors vendor = new Vendors();
        vendor.setId(77L);
        vendor.setBusinessName("Emerald Convention Centre");
        vendor.setEmail("owner@example.com");
        vendor.setContactNumber("9876501234");

        VendorHallDetails hall = new VendorHallDetails();
        hall.setId(201L);
        hall.setVendor(vendor);
        hall.setAmount(new BigDecimal("125000.00"));
        return hall;
    }

    private User customer() {
        User user = new User();
        user.setId(101L);
        user.setFullName("Priya Raman");
        user.setPhone("9876543210");
        user.setEmail("priya@example.com");
        user.setStatus("ACTIVE");
        return user;
    }

    private User otherCustomer() {
        User user = customer();
        user.setId(102L);
        return user;
    }

    private User owner() {
        User user = new User();
        user.setId(301L);
        user.setFullName("Arun Kumar");
        user.setPhone("9876501234");
        user.setEmail("owner@example.com");
        user.setStatus("ACTIVE");
        return user;
    }

    private UsernamePasswordAuthenticationToken auth(Long userId, UserRole role) {
        return new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
