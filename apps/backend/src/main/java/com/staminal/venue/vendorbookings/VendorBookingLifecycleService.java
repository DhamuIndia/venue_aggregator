package com.staminal.venue.vendorbookings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
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
import com.staminal.venue.enums.VendorLeadStatus;
import com.staminal.venue.enums.VendorServiceBookingStatus;
import com.staminal.venue.leads.VendorLeadRepository;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.notifications.NotificationType;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;
import com.staminal.venue.vendorbookings.dto.VendorServiceBookingResponse;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class VendorBookingLifecycleService {

    private final VendorServiceBookingRepository bookingRepository;
    private final VendorBookingPaymentRepository paymentRepository;
    private final VendorBookingTimelineRepository timelineRepository;
    private final VendorBookingPaymentGateway paymentGateway;
    private final VendorLeadRepository leadRepository;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final VendorServiceBookingService bookingService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Value("${app.vendor-bookings.cancellation.full-refund-days:7}")
    private int fullRefundDays;

    @Value("${app.vendor-bookings.cancellation.partial-refund-days:3}")
    private int partialRefundDays;

    @Value("${app.vendor-bookings.cancellation.partial-refund-percent:50}")
    private int partialRefundPercent;

    public VendorServiceBookingResponse updateVendorStatus(
            String bookingId,
            VendorServiceBookingStatus target,
            String reason,
            Authentication authentication) {
        User user = currentUser(authentication, UserRole.VENDOR);
        Vendors vendor = vendorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor profile not found"));
        VendorServiceBooking booking = bookingForUpdate(bookingId);
        if (!booking.getVendor().getId().equals(vendor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot manage this booking");
        }
        if (target == VendorServiceBookingStatus.CANCELLED) {
            return cancel(booking, user, UserRole.VENDOR, reason);
        }

        VendorServiceBookingStatus oldStatus = booking.getStatus();
        if (oldStatus == VendorServiceBookingStatus.CONFIRMED
                && target == VendorServiceBookingStatus.IN_PROGRESS) {
            if (booking.getPaymentStatus() != PaymentStatus.ADVANCE_PAID) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Advance payment is required before work starts");
            }
            booking.setStartedAt(Instant.now());
        } else if (oldStatus == VendorServiceBookingStatus.IN_PROGRESS
                && target == VendorServiceBookingStatus.COMPLETED) {
            booking.setCompletedAt(Instant.now());
            booking.getLead().setStatus(VendorLeadStatus.COMPLETED);
            leadRepository.save(booking.getLead());
        } else {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid booking transition from " + oldStatus + " to " + target);
        }

        booking.setStatus(target);
        booking = bookingRepository.save(booking);
        recordStatusTimeline(booking, user, UserRole.VENDOR, oldStatus, target, statusMessage(target));
        notifyStatusChange(booking, target);
        recordStatusAudit(booking, user, UserRole.VENDOR, oldStatus, target);
        return bookingService.toResponse(booking);
    }

    public VendorServiceBookingResponse cancelCustomerBooking(
            String bookingId,
            String reason,
            Authentication authentication) {
        User customer = currentUser(authentication, UserRole.CUSTOMER);
        VendorServiceBooking booking = bookingForUpdate(bookingId);
        if (!booking.getCustomer().getId().equals(customer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot manage this booking");
        }
        return cancel(booking, customer, UserRole.CUSTOMER, reason);
    }

    private VendorServiceBookingResponse cancel(
            VendorServiceBooking booking,
            User actor,
            UserRole actorRole,
            String reason) {
        if (booking.getStatus() == VendorServiceBookingStatus.CANCELLED) {
            return bookingService.toResponse(booking);
        }
        if (booking.getStatus() == VendorServiceBookingStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A completed booking cannot be cancelled");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation reason is required");
        }

        VendorServiceBookingStatus oldStatus = booking.getStatus();
        BigDecimal paidAmount = paymentRepository.findByBooking_IdOrderByCreatedAtDesc(booking.getId())
                .stream()
                .filter(payment -> payment.getStatus() == VendorBookingPaymentStatus.PAID)
                .map(VendorBookingPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundable = refundableAmount(booking.getEventDate(), paidAmount);
        Instant now = Instant.now();

        booking.setStatus(VendorServiceBookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        booking.setCancelledBy(actorRole.name());
        booking.setCancellationReason(normalizedReason);
        booking.setRefundableAmount(refundable);
        if (refundable.signum() > 0) {
            booking.setPaymentStatus(PaymentStatus.REFUND_PENDING);
            markRefundPending(booking, refundable, now);
            attemptAutomaticRefund(booking, paidAmount, now);
        }
        booking.getLead().setStatus(VendorLeadStatus.DECLINED);
        leadRepository.save(booking.getLead());
        booking = bookingRepository.save(booking);

        boolean refundProcessed = booking.getPaymentStatus() == PaymentStatus.REFUNDED
                || booking.getPaymentStatus() == PaymentStatus.PARTIALLY_REFUNDED;
        String message = refundable.signum() > 0
                ? "Booking cancelled. Refund of INR " + refundable.setScale(2)
                        + (refundProcessed ? " was processed." : " is pending.")
                : "Booking cancelled. The payment is outside the refundable window.";
        recordStatusTimeline(booking, actor, actorRole, oldStatus, VendorServiceBookingStatus.CANCELLED, message);
        notifyCancellation(booking, actorRole, refundable, refundProcessed);
        auditService.record(new AuditCommand(
                actor.getId(),
                actorRole.name(),
                AuditAction.VENDOR_SERVICE_BOOKING_CANCELLED,
                "VENDOR_SERVICE_BOOKING",
                String.valueOf(booking.getId()),
                "Vendor service booking cancelled",
                Map.of("status", oldStatus.name()),
                Map.of("status", VendorServiceBookingStatus.CANCELLED.name()),
                Map.of(
                        "reason", normalizedReason,
                        "refundableAmount", refundable,
                        "refundRule", refundRule(booking.getEventDate()))));
        if (refundable.signum() > 0) {
            auditService.record(new AuditCommand(
                    actor.getId(),
                    actorRole.name(),
                    AuditAction.VENDOR_BOOKING_REFUND_REQUESTED,
                    "VENDOR_SERVICE_BOOKING",
                    String.valueOf(booking.getId()),
                    "Vendor booking refund queued",
                    Map.of(),
                    Map.of("paymentStatus", booking.getPaymentStatus().name()),
                    Map.of("amount", refundable)));
        }
        return bookingService.toResponse(booking);
    }

    private void markRefundPending(VendorServiceBooking booking, BigDecimal refundable, Instant now) {
        BigDecimal remaining = refundable;
        for (VendorBookingPayment payment : paymentRepository.findByBooking_IdOrderByCreatedAtDesc(booking.getId())) {
            if (remaining.signum() <= 0 || payment.getStatus() != VendorBookingPaymentStatus.PAID) continue;
            BigDecimal paymentRefund = payment.getAmount().min(remaining);
            payment.setRefundStatus(VendorBookingRefundStatus.PENDING);
            payment.setRefundAmount(paymentRefund);
            payment.setRefundRequestedAt(now);
            paymentRepository.save(payment);
            remaining = remaining.subtract(paymentRefund);
        }
    }

    private void attemptAutomaticRefund(
            VendorServiceBooking booking,
            BigDecimal paidAmount,
            Instant now) {
        if (!paymentGateway.isConfigured()) return;
        boolean allProcessed = true;
        for (VendorBookingPayment payment : paymentRepository.findByBooking_IdOrderByCreatedAtDesc(booking.getId())) {
            if (payment.getRefundStatus() != VendorBookingRefundStatus.PENDING) continue;
            if (payment.getProviderPaymentId() == null || payment.getProviderPaymentId().isBlank()) {
                allProcessed = false;
                continue;
            }
            try {
                var result = paymentGateway.refund(payment.getProviderPaymentId(), payment.getRefundAmount());
                payment.setProviderRefundId(result.refundId());
                if ("processed".equalsIgnoreCase(result.status())
                        || "refunded".equalsIgnoreCase(result.status())) {
                    payment.setRefundedAt(now);
                    payment.setRefundStatus(payment.getRefundAmount().compareTo(payment.getAmount()) >= 0
                            ? VendorBookingRefundStatus.REFUNDED
                            : VendorBookingRefundStatus.PARTIALLY_REFUNDED);
                } else {
                    allProcessed = false;
                }
                paymentRepository.save(payment);
            } catch (RuntimeException exception) {
                allProcessed = false;
            }
        }
        if (allProcessed) {
            booking.setPaymentStatus(booking.getRefundableAmount().compareTo(paidAmount) >= 0
                    ? PaymentStatus.REFUNDED
                    : PaymentStatus.PARTIALLY_REFUNDED);
        }
    }

    private BigDecimal refundableAmount(LocalDate eventDate, BigDecimal paidAmount) {
        if (paidAmount.signum() <= 0) return BigDecimal.ZERO.setScale(2);
        long days = ChronoUnit.DAYS.between(LocalDate.now(), eventDate);
        if (days >= fullRefundDays) return paidAmount.setScale(2, RoundingMode.HALF_UP);
        if (days >= partialRefundDays) {
            return paidAmount
                    .multiply(BigDecimal.valueOf(partialRefundPercent))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2);
    }

    private String refundRule(LocalDate eventDate) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), eventDate);
        if (days >= fullRefundDays) return "FULL";
        if (days >= partialRefundDays) return "PARTIAL_" + partialRefundPercent;
        return "NONE";
    }

    private void notifyStatusChange(VendorServiceBooking booking, VendorServiceBookingStatus target) {
        String title = target == VendorServiceBookingStatus.COMPLETED
                ? "Vendor service completed"
                : "Vendor service is in progress";
        notificationService.notifyUser(
                booking.getCustomer(),
                NotificationType.BOOKING,
                title,
                booking.getVendor().getBusinessName() + " updated your " + booking.getService() + " booking.",
                "/customer?tab=bookings");
        if (target == VendorServiceBookingStatus.COMPLETED) {
            notificationService.notifyUser(
                    booking.getCustomer(),
                    NotificationType.REVIEW,
                    "Share your experience",
                    "Your service is complete. You can now review " + booking.getVendor().getBusinessName() + ".",
                    "/customer?tab=reviews");
        }
    }

    private void notifyCancellation(
            VendorServiceBooking booking,
            UserRole actorRole,
            BigDecimal refundable,
            boolean refundProcessed) {
        User recipient = actorRole == UserRole.CUSTOMER
                ? booking.getVendor().getUser()
                : booking.getCustomer();
        notificationService.notifyUser(
                recipient,
                NotificationType.BOOKING,
                "Vendor booking cancelled",
                "The " + booking.getService() + " booking was cancelled."
                        + (refundable.signum() > 0
                                ? refundProcessed ? " The refund was processed." : " Refund processing is pending."
                                : ""),
                actorRole == UserRole.CUSTOMER ? "/vendor?tab=bookings" : "/customer?tab=bookings");
    }

    private void recordStatusTimeline(
            VendorServiceBooking booking,
            User actor,
            UserRole actorRole,
            VendorServiceBookingStatus from,
            VendorServiceBookingStatus to,
            String message) {
        VendorBookingTimeline item = new VendorBookingTimeline();
        item.setBooking(booking);
        item.setEventType(to == VendorServiceBookingStatus.CANCELLED ? "CANCELLED" : "STATUS_CHANGED");
        item.setFromStatus(from.name());
        item.setToStatus(to.name());
        item.setActor(actor);
        item.setActorRole(actorRole.name());
        item.setMessage(message);
        timelineRepository.save(item);
    }

    private void recordStatusAudit(
            VendorServiceBooking booking,
            User actor,
            UserRole actorRole,
            VendorServiceBookingStatus from,
            VendorServiceBookingStatus to) {
        auditService.record(new AuditCommand(
                actor.getId(),
                actorRole.name(),
                AuditAction.VENDOR_SERVICE_BOOKING_STATUS_CHANGED,
                "VENDOR_SERVICE_BOOKING",
                String.valueOf(booking.getId()),
                "Vendor service booking status changed",
                Map.of("status", from.name()),
                Map.of("status", to.name()),
                Map.of("leadId", booking.getLead().getId(), "vendorId", booking.getVendor().getId())));
    }

    private String statusMessage(VendorServiceBookingStatus status) {
        return status == VendorServiceBookingStatus.COMPLETED
                ? "Vendor marked the service as completed"
                : "Vendor started work on the service";
    }

    private VendorServiceBooking bookingForUpdate(String bookingId) {
        return bookingRepository.findForUpdate(VendorBookingPaymentService.parseBookingId(bookingId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor booking not found"));
    }

    private User currentUser(Authentication authentication, UserRole role) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                        .noneMatch(authority -> ("ROLE_" + role.name()).equals(authority.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, role.name() + " role is required");
        }
        try {
            return userRepository.findById(Long.valueOf(authentication.getName()))
                    .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user session"));
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user session");
        }
    }
}
