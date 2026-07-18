package com.staminal.venue.enquiries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.staminal.venue.bookings.Booking;
import com.staminal.venue.bookings.BookingRepository;
import com.staminal.venue.enquiries.dto.CreateEnquiryRequest;
import com.staminal.venue.enquiries.dto.EnquiryResponse;
import com.staminal.venue.enquiries.dto.EnquirySlotRequestDto;
import com.staminal.venue.enquiries.dto.UpdateEnquiryStatusRequest;
import com.staminal.venue.enums.EnquiryStatus;
import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.enums.SlotType;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallBlockedDateRepository;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.notifications.NotificationType;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class EnquiryServiceTest {

    @Mock
    private EnquiryRepository enquiryRepository;

    @Mock
    private HallRepository hallRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private HallBlockedDateRepository hallBlockedDateRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    private EnquiryService enquiryService;

    @BeforeEach
    void setUp() {
        enquiryService = new EnquiryService(
                enquiryRepository,
                hallRepository,
                bookingRepository,
                hallBlockedDateRepository,
                userRepository,
                notificationService);
    }

    @Test
    void customerCanCreateHallEnquiry() {
        User customer = customer();
        Halls hall = hall();
        CreateEnquiryRequest request = new CreateEnquiryRequest(
                "emerald-convention-centre",
                LocalDate.now().plusDays(10),
                "Wedding",
                450,
                SlotType.EVENING,
                null,
                "Please share catering options.");

        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(hallRepository.findAll()).thenReturn(List.of(hall));
        when(enquiryRepository.save(any(Enquiry.class))).thenAnswer(invocation -> {
            Enquiry enquiry = invocation.getArgument(0);
            enquiry.setId(55L);
            enquiry.setCreatedAt(Instant.parse("2026-06-25T10:30:00Z"));
            enquiry.setUpdatedAt(Instant.parse("2026-06-25T10:30:00Z"));
            enquiry.setVersion(0L);
            return enquiry;
        });

        EnquiryResponse response = enquiryService.createHallEnquiry(request, auth(101L, UserRole.CUSTOMER));

        ArgumentCaptor<Enquiry> enquiryCaptor = ArgumentCaptor.forClass(Enquiry.class);
        verify(enquiryRepository).save(enquiryCaptor.capture());
        Enquiry saved = enquiryCaptor.getValue();

        assertThat(saved.getCustomer()).isEqualTo(customer);
        assertThat(saved.getHall()).isEqualTo(hall);
        assertThat(saved.getStatus()).isEqualTo(EnquiryStatus.PENDING_OWNER_RESPONSE);
        assertThat(saved.getSlotRequests()).hasSize(1);
        assertThat(saved.getSlotRequests().get(0).getSlotType()).isEqualTo(SlotType.EVENING);
        assertThat(response.id()).isEqualTo("ENQ-000055");
        assertThat(response.hallName()).isEqualTo("Emerald Convention Centre");
        assertThat(response.customerId()).isEqualTo("101");
        assertThat(response.slotRequests()).hasSize(1);
        verify(notificationService).notifyUser(
                customer,
                NotificationType.ENQUIRY,
                "Enquiry submitted",
                "Your enquiry for Emerald Convention Centre was sent to the owner.",
                "/customer?tab=enquiries");
        verify(notificationService).notifyUser(
                hall.getOwnerUserId(),
                NotificationType.ENQUIRY,
                "New enquiry received",
                "Priya Raman enquired for Emerald Convention Centre.",
                "/owner?tab=enquiries");
    }

    @Test
    void customerCanCreateMultiSlotHallEnquiry() {
        User customer = customer();
        Halls hall = hall();
        LocalDate eventDate = LocalDate.now().plusDays(10);
        CreateEnquiryRequest request = new CreateEnquiryRequest(
                "emerald-convention-centre",
                eventDate,
                "Wedding",
                450,
                SlotType.EVENING,
                List.of(
                        new EnquirySlotRequestDto(eventDate, SlotType.EVENING, "Wedding"),
                        new EnquirySlotRequestDto(eventDate.plusDays(1), SlotType.MORNING, "Reception")),
                "Need evening and next morning.");

        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(hallRepository.findAll()).thenReturn(List.of(hall));
        when(enquiryRepository.save(any(Enquiry.class))).thenAnswer(invocation -> {
            Enquiry enquiry = invocation.getArgument(0);
            enquiry.setId(56L);
            enquiry.setCreatedAt(Instant.parse("2026-06-25T10:30:00Z"));
            enquiry.setUpdatedAt(Instant.parse("2026-06-25T10:30:00Z"));
            enquiry.setVersion(0L);
            return enquiry;
        });

        EnquiryResponse response = enquiryService.createHallEnquiry(request, auth(101L, UserRole.CUSTOMER));

        ArgumentCaptor<Enquiry> enquiryCaptor = ArgumentCaptor.forClass(Enquiry.class);
        verify(enquiryRepository).save(enquiryCaptor.capture());
        Enquiry saved = enquiryCaptor.getValue();

        assertThat(saved.getSlotRequests()).hasSize(2);
        assertThat(saved.getSlotRequests())
                .extracting(EnquirySlotRequest::getSlotType)
                .containsExactly(SlotType.EVENING, SlotType.MORNING);
        assertThat(response.slotRequests()).hasSize(2);
        assertThat(response.slotRequests().get(1).date()).isEqualTo(eventDate.plusDays(1));
    }

    @Test
    void customerCannotReadAnotherCustomersEnquiry() {
        Enquiry enquiry = enquiry();
        enquiry.setCustomer(otherCustomer());

        when(userRepository.findById(101L)).thenReturn(Optional.of(customer()));
        when(enquiryRepository.findById(55L)).thenReturn(Optional.of(enquiry));

        assertThatThrownBy(() -> enquiryService.getCustomerEnquiry("ENQ-000055", auth(101L, UserRole.CUSTOMER)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    void ownerCanConfirmEnquiryAndCreateBooking() {
        User owner = owner();
        Enquiry enquiry = enquiry();
        enquiry.setStatus(EnquiryStatus.PENDING_OWNER_RESPONSE);

        when(userRepository.findById(301L)).thenReturn(Optional.of(owner));
        when(enquiryRepository.findById(55L)).thenReturn(Optional.of(enquiry));
        when(bookingRepository.findByEnquiry_Id(55L)).thenReturn(Optional.empty());
        when(bookingRepository.findByHall_IdAndStatus(201L, Booking.STATUS_CONFIRMED)).thenReturn(List.of());
        when(hallBlockedDateRepository.findByHallId_Id(201L)).thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(enquiryRepository.save(any(Enquiry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EnquiryResponse response = enquiryService.updateOwnerEnquiryStatus(
                "ENQ-000055",
                new UpdateEnquiryStatusRequest(EnquiryStatus.CONFIRMED, "Available for the evening slot."),
                auth(301L, UserRole.HALL_OWNER));

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(bookingCaptor.capture());

        assertThat(response.status()).isEqualTo(EnquiryStatus.CONFIRMED);
        assertThat(bookingCaptor.getValue().getEnquiry()).isEqualTo(enquiry);
        assertThat(bookingCaptor.getValue().getHall()).isEqualTo(enquiry.getHall());
        assertThat(bookingCaptor.getValue().getStatus()).isEqualTo(Booking.STATUS_CONFIRMED);
        verify(notificationService).notifyUser(
                enquiry.getCustomer(),
                NotificationType.BOOKING,
                "Booking confirmed",
                "Emerald Convention Centre confirmed your Wedding enquiry.",
                "/customer?tab=bookings");
    }

    @Test
    void terminalDeclinedEnquiryCannotBeCompleted() {
        User owner = owner();
        Enquiry enquiry = enquiry();
        enquiry.setStatus(EnquiryStatus.DECLINED);

        when(userRepository.findById(301L)).thenReturn(Optional.of(owner));
        when(enquiryRepository.findById(55L)).thenReturn(Optional.of(enquiry));

        assertThatThrownBy(() -> enquiryService.updateOwnerEnquiryStatus(
                "55",
                new UpdateEnquiryStatusRequest(EnquiryStatus.COMPLETED, null),
                auth(301L, UserRole.HALL_OWNER)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    private Enquiry enquiry() {
        Enquiry enquiry = new Enquiry();
        enquiry.setId(55L);
        enquiry.setHall(hall());
        enquiry.setCustomer(customer());
        enquiry.setCustomerName("Priya Raman");
        enquiry.setCustomerPhone("9876543210");
        enquiry.setCustomerEmail("priya@example.com");
        enquiry.setEventDate(LocalDate.now().plusDays(15));
        enquiry.setEventType("Wedding");
        enquiry.setGuestCount(450);
        enquiry.setSlotType(SlotType.EVENING);
        enquiry.setMessage("Please share catering options.");
        enquiry.setCreatedAt(Instant.parse("2026-06-25T10:30:00Z"));
        enquiry.setUpdatedAt(Instant.parse("2026-06-25T10:30:00Z"));
        enquiry.setVersion(0L);
        return enquiry;
    }

    private Halls hall() {
        Halls hall = new Halls();
        hall.setId(201L);
        hall.setName("Emerald Convention Centre");
        hall.setOwnerUserId(owner());
        hall.setCity("Chennai");
        hall.setArea("ECR");
        hall.setCapacityMax(900);
        hall.setHallType("MARRIAGE_HALL");
        hall.setFullDayAmount(new java.math.BigDecimal("125000.00"));
        hall.setStatus(HallStatus.APPROVED);
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
