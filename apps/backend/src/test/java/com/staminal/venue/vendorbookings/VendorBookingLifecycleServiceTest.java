package com.staminal.venue.vendorbookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.PaymentStatus;
import com.staminal.venue.enums.VendorLeadStatus;
import com.staminal.venue.enums.VendorServiceBookingStatus;
import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.leads.VendorLeadRepository;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

@ExtendWith(MockitoExtension.class)
class VendorBookingLifecycleServiceTest {

    @Mock VendorServiceBookingRepository bookingRepository;
    @Mock VendorBookingPaymentRepository paymentRepository;
    @Mock VendorBookingTimelineRepository timelineRepository;
    @Mock VendorBookingPaymentGateway paymentGateway;
    @Mock VendorLeadRepository leadRepository;
    @Mock VendorRepository vendorRepository;
    @Mock UserRepository userRepository;
    @Mock VendorServiceBookingService bookingService;
    @Mock NotificationService notificationService;
    @Mock AuditService auditService;

    private VendorBookingLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new VendorBookingLifecycleService(
                bookingRepository,
                paymentRepository,
                timelineRepository,
                paymentGateway,
                leadRepository,
                vendorRepository,
                userRepository,
                bookingService,
                notificationService,
                auditService);
        ReflectionTestUtils.setField(service, "fullRefundDays", 7);
        ReflectionTestUtils.setField(service, "partialRefundDays", 3);
        ReflectionTestUtils.setField(service, "partialRefundPercent", 50);
    }

    @Test
    void vendorStartsPaidConfirmedBooking() {
        User vendorUser = user(301L, "Vendor");
        Vendors vendor = vendor(vendorUser);
        VendorServiceBooking booking = booking(vendor, user(101L, "Customer"));
        booking.setPaymentStatus(PaymentStatus.ADVANCE_PAID);

        when(userRepository.findById(301L)).thenReturn(Optional.of(vendorUser));
        when(vendorRepository.findByUserId(301L)).thenReturn(Optional.of(vendor));
        when(bookingRepository.findForUpdate(901L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateVendorStatus(
                "VBOOK-000901",
                VendorServiceBookingStatus.IN_PROGRESS,
                null,
                auth(301L, "VENDOR"));

        assertThat(booking.getStatus()).isEqualTo(VendorServiceBookingStatus.IN_PROGRESS);
        assertThat(booking.getStartedAt()).isNotNull();
        verify(timelineRepository).save(any(VendorBookingTimeline.class));
        verify(auditService).record(any());
    }

    @Test
    void customerCancellationQueuesFullRefundOutsideSevenDays() {
        User customer = user(101L, "Customer");
        Vendors vendor = vendor(user(301L, "Vendor"));
        VendorServiceBooking booking = booking(vendor, customer);
        booking.setEventDate(LocalDate.now().plusDays(10));
        booking.setPaymentStatus(PaymentStatus.ADVANCE_PAID);

        VendorBookingPayment payment = new VendorBookingPayment();
        payment.setBooking(booking);
        payment.setAmount(new BigDecimal("20000.00"));
        payment.setStatus(VendorBookingPaymentStatus.PAID);
        payment.setRefundStatus(VendorBookingRefundStatus.NOT_REQUESTED);
        payment.setRefundAmount(BigDecimal.ZERO);

        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(bookingRepository.findForUpdate(901L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBooking_IdOrderByCreatedAtDesc(901L)).thenReturn(List.of(payment));
        when(bookingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.cancelCustomerBooking(
                "VBOOK-000901",
                "Event date changed",
                auth(101L, "CUSTOMER"));

        assertThat(booking.getStatus()).isEqualTo(VendorServiceBookingStatus.CANCELLED);
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        assertThat(booking.getRefundableAmount()).isEqualByComparingTo("20000.00");
        assertThat(payment.getRefundStatus()).isEqualTo(VendorBookingRefundStatus.PENDING);
        assertThat(payment.getRefundAmount()).isEqualByComparingTo("20000.00");
        assertThat(booking.getLead().getStatus()).isEqualTo(VendorLeadStatus.DECLINED);
    }

    private VendorServiceBooking booking(Vendors vendor, User customer) {
        VendorLead lead = new VendorLead();
        lead.setId(701L);
        lead.setStatus(VendorLeadStatus.BOOKED);
        VendorServiceBooking booking = new VendorServiceBooking();
        booking.setId(901L);
        booking.setVendor(vendor);
        booking.setCustomer(customer);
        booking.setLead(lead);
        booking.setService("Photography");
        booking.setEventDate(LocalDate.now().plusDays(20));
        booking.setStatus(VendorServiceBookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.ADVANCE_PENDING);
        booking.setRefundableAmount(BigDecimal.ZERO);
        return booking;
    }

    private Vendors vendor(User user) {
        Vendors vendor = new Vendors();
        vendor.setId(501L);
        vendor.setUser(user);
        vendor.setBusinessName("Wedding Stories");
        return vendor;
    }

    private User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        user.setStatus("ACTIVE");
        return user;
    }

    private UsernamePasswordAuthenticationToken auth(Long id, String role) {
        return new UsernamePasswordAuthenticationToken(
                String.valueOf(id),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
