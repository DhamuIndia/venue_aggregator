package com.staminal.venue.vendorbookings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
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

import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.PaymentStatus;
import com.staminal.venue.enums.VendorLeadStatus;
import com.staminal.venue.enums.VendorServiceBookingStatus;
import com.staminal.venue.leads.VendorLead;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendorbookings.dto.VerifyVendorBookingPaymentRequest;
import com.staminal.venue.vendors.Entity.Vendors;

@ExtendWith(MockitoExtension.class)
class VendorBookingPaymentServiceTest {

    @Mock VendorServiceBookingRepository bookingRepository;
    @Mock VendorBookingPaymentRepository paymentRepository;
    @Mock VendorBookingTimelineRepository timelineRepository;
    @Mock VendorBookingPaymentGateway paymentGateway;
    @Mock VendorServiceBookingService bookingService;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;
    @Mock AuditService auditService;

    private VendorBookingPaymentService service;

    @BeforeEach
    void setUp() {
        service = new VendorBookingPaymentService(
                bookingRepository,
                paymentRepository,
                timelineRepository,
                paymentGateway,
                bookingService,
                userRepository,
                notificationService,
                auditService);
    }

    @Test
    void createsRazorpayOrderForConfiguredAdvanceAmount() {
        User customer = user(101L, "Customer");
        VendorServiceBooking booking = booking(customer);
        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(bookingRepository.findForUpdate(901L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findFirstByBooking_IdAndPaymentTypeAndStatusOrderByCreatedAtDesc(
                901L,
                "ADVANCE",
                VendorBookingPaymentStatus.CREATED)).thenReturn(Optional.empty());
        when(paymentGateway.createOrder(any(), any()))
                .thenReturn(new VendorBookingPaymentGateway.PaymentOrder("order_123", "rzp_test_123", "INR"));
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            VendorBookingPayment payment = invocation.getArgument(0);
            payment.setId(801L);
            return payment;
        });

        var response = service.createAdvanceOrder("VBOOK-000901", customerAuth());

        assertThat(response.orderId()).isEqualTo("order_123");
        assertThat(response.amount()).isEqualByComparingTo("20000.00");
        assertThat(response.keyId()).isEqualTo("rzp_test_123");
        verify(timelineRepository).save(any(VendorBookingTimeline.class));
    }

    @Test
    void verifiedSignatureMarksAdvancePaidAndCreatesReceipt() {
        User customer = user(101L, "Customer");
        VendorServiceBooking booking = booking(customer);
        VendorBookingPayment payment = new VendorBookingPayment();
        payment.setId(801L);
        payment.setBooking(booking);
        payment.setPaymentType("ADVANCE");
        payment.setAmount(new BigDecimal("20000.00"));
        payment.setCurrency("INR");
        payment.setStatus(VendorBookingPaymentStatus.CREATED);
        payment.setProviderOrderId("order_123");

        when(userRepository.findById(101L)).thenReturn(Optional.of(customer));
        when(bookingRepository.findForUpdate(901L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByProviderOrderIdAndBooking_Id("order_123", 901L))
                .thenReturn(Optional.of(payment));
        when(paymentGateway.verify("order_123", "pay_123", "signature")).thenReturn(true);
        when(bookingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.verify(
                "VBOOK-000901",
                new VerifyVendorBookingPaymentRequest("order_123", "pay_123", "signature"),
                customerAuth());

        assertThat(payment.getStatus()).isEqualTo(VendorBookingPaymentStatus.PAID);
        assertThat(payment.getReceiptNumber()).startsWith("VM-");
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.ADVANCE_PAID);
        verify(notificationService, times(2)).notifyUser(
                any(),
                any(),
                any(),
                any(),
                any());
    }

    private VendorServiceBooking booking(User customer) {
        User vendorUser = user(301L, "Vendor");
        Vendors vendor = new Vendors();
        vendor.setId(501L);
        vendor.setUser(vendorUser);
        vendor.setBusinessName("Wedding Stories");
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
        booking.setAdvanceAmount(new BigDecimal("20000.00"));
        booking.setStatus(VendorServiceBookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.ADVANCE_PENDING);
        return booking;
    }

    private User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        user.setStatus("ACTIVE");
        return user;
    }

    private UsernamePasswordAuthenticationToken customerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "101",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }
}
