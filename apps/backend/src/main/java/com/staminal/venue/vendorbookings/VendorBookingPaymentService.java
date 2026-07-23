package com.staminal.venue.vendorbookings;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.PaymentStatus;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.enums.VendorServiceBookingStatus;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.notifications.NotificationType;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendorbookings.dto.VendorBookingPaymentOrderResponse;
import com.staminal.venue.vendorbookings.dto.VendorServiceBookingResponse;
import com.staminal.venue.vendorbookings.dto.VerifyVendorBookingPaymentRequest;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class VendorBookingPaymentService {

    private final VendorServiceBookingRepository bookingRepository;
    private final VendorBookingPaymentRepository paymentRepository;
    private final VendorBookingTimelineRepository timelineRepository;
    private final VendorBookingPaymentGateway paymentGateway;
    private final VendorServiceBookingService bookingService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public VendorBookingPaymentOrderResponse createAdvanceOrder(String bookingId, Authentication authentication) {
        User customer = currentCustomer(authentication);
        VendorServiceBooking booking = ownedBookingForUpdate(bookingId, customer);
        if (booking.getStatus() != VendorServiceBookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a confirmed booking can be paid");
        }
        if (booking.getPaymentStatus() == PaymentStatus.ADVANCE_PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The advance is already paid");
        }

        var existing = paymentRepository
                .findFirstByBooking_IdAndPaymentTypeAndStatusOrderByCreatedAtDesc(
                        booking.getId(),
                        "ADVANCE",
                        VendorBookingPaymentStatus.CREATED);
        if (existing.isPresent()) {
            VendorBookingPayment payment = existing.get();
            return orderResponse(booking, payment, paymentGateway.publicKey());
        }

        String receipt = "VBOOK-" + booking.getId() + "-ADV-" + Instant.now().getEpochSecond();
        VendorBookingPaymentGateway.PaymentOrder order = paymentGateway.createOrder(receipt, booking.getAdvanceAmount());

        VendorBookingPayment payment = new VendorBookingPayment();
        payment.setBooking(booking);
        payment.setPaymentType("ADVANCE");
        payment.setAmount(booking.getAdvanceAmount());
        payment.setCurrency(order.currency());
        payment.setStatus(VendorBookingPaymentStatus.CREATED);
        payment.setProvider("RAZORPAY");
        payment.setProviderOrderId(order.orderId());
        payment = paymentRepository.save(payment);

        recordTimeline(booking, customer, "PAYMENT_ORDER_CREATED", "Advance payment order created");
        auditService.record(new AuditCommand(
                customer.getId(),
                UserRole.CUSTOMER.name(),
                AuditAction.VENDOR_BOOKING_PAYMENT_ORDER_CREATED,
                "VENDOR_SERVICE_BOOKING",
                String.valueOf(booking.getId()),
                "Customer created vendor booking advance order",
                Map.of("paymentStatus", booking.getPaymentStatus().name()),
                Map.of("paymentStatus", PaymentStatus.ADVANCE_PENDING.name()),
                Map.of("paymentId", payment.getId(), "amount", payment.getAmount())));

        return orderResponse(booking, payment, order.keyId());
    }

    public VendorServiceBookingResponse verify(
            String bookingId,
            VerifyVendorBookingPaymentRequest request,
            Authentication authentication) {
        User customer = currentCustomer(authentication);
        VendorServiceBooking booking = ownedBookingForUpdate(bookingId, customer);
        VendorBookingPayment payment = paymentRepository
                .findByProviderOrderIdAndBooking_Id(request.orderId(), booking.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found"));

        if (payment.getStatus() == VendorBookingPaymentStatus.PAID) {
            return bookingService.toResponse(booking);
        }
        if (booking.getStatus() != VendorServiceBookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking can no longer be paid");
        }
        if (!paymentGateway.verify(
                payment.getProviderOrderId(),
                request.razorpayPaymentId(),
                request.razorpaySignature())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment verification failed");
        }

        Instant paidAt = Instant.now();
        payment.setStatus(VendorBookingPaymentStatus.PAID);
        payment.setProviderPaymentId(request.razorpayPaymentId().trim());
        payment.setProviderSignature(request.razorpaySignature().trim());
        payment.setPaidAt(paidAt);
        payment.setReceiptNumber("VM-" + paidAt.toString().substring(0, 10).replace("-", "")
                + "-V" + String.format("%06d", booking.getId())
                + "-P" + String.format("%06d", payment.getId()));
        paymentRepository.save(payment);

        PaymentStatus oldStatus = booking.getPaymentStatus();
        booking.setPaymentStatus(PaymentStatus.ADVANCE_PAID);
        bookingRepository.save(booking);
        recordTimeline(booking, customer, "PAYMENT_RECEIVED",
                "Advance payment received. Receipt " + payment.getReceiptNumber());

        notificationService.notifyUser(
                booking.getVendor().getUser(),
                NotificationType.PAYMENT,
                "Advance payment received",
                customer.getFullName() + " paid the advance for " + booking.getService() + ".",
                "/vendor?tab=bookings");
        notificationService.notifyUser(
                customer,
                NotificationType.PAYMENT,
                "Advance payment successful",
                "Your advance for " + booking.getVendor().getBusinessName() + " was received.",
                "/customer?tab=bookings");

        auditService.record(new AuditCommand(
                customer.getId(),
                UserRole.CUSTOMER.name(),
                AuditAction.VENDOR_BOOKING_PAYMENT_VERIFIED,
                "VENDOR_SERVICE_BOOKING",
                String.valueOf(booking.getId()),
                "Vendor booking advance payment verified",
                Map.of("paymentStatus", oldStatus.name()),
                Map.of("paymentStatus", booking.getPaymentStatus().name()),
                Map.of("paymentId", payment.getId(), "receiptNumber", payment.getReceiptNumber())));
        return bookingService.toResponse(booking);
    }

    private VendorBookingPaymentOrderResponse orderResponse(
            VendorServiceBooking booking,
            VendorBookingPayment payment,
            String keyId) {
        return new VendorBookingPaymentOrderResponse(
                payment.getProviderOrderId(),
                formatBookingId(booking.getId()),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name(),
                keyId);
    }

    private void recordTimeline(VendorServiceBooking booking, User actor, String eventType, String message) {
        VendorBookingTimeline item = new VendorBookingTimeline();
        item.setBooking(booking);
        item.setEventType(eventType);
        item.setActor(actor);
        item.setActorRole(UserRole.CUSTOMER.name());
        item.setMessage(message);
        timelineRepository.save(item);
    }

    private VendorServiceBooking ownedBookingForUpdate(String bookingId, User customer) {
        VendorServiceBooking booking = bookingRepository.findForUpdate(parseBookingId(bookingId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor booking not found"));
        if (!booking.getCustomer().getId().equals(customer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot manage this booking");
        }
        return booking;
    }

    private User currentCustomer(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                        .noneMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CUSTOMER role is required");
        }
        try {
            return userRepository.findById(Long.valueOf(authentication.getName()))
                    .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user session"));
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user session");
        }
    }

    static Long parseBookingId(String value) {
        try {
            return Long.valueOf(value == null ? "" : value.trim().replaceFirst("(?i)^VBOOK-", ""));
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid vendor booking id");
        }
    }

    static String formatBookingId(Long id) {
        return "VBOOK-" + String.format("%06d", id);
    }
}
