package com.staminal.venue.vendorbookings;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staminal.venue.audit.AuditAction;
import com.staminal.venue.audit.AuditCommand;
import com.staminal.venue.audit.AuditService;
import com.staminal.venue.enums.VendorServiceBookingStatus;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.notifications.NotificationType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorBookingReminderService {

    private final VendorServiceBookingRepository bookingRepository;
    private final VendorBookingTimelineRepository timelineRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Scheduled(cron = "${app.vendor-bookings.reminders.cron:0 15 9 * * *}", zone = "Asia/Kolkata")
    @Transactional
    public void sendEventReminders() {
        sendForDays(7);
        sendForDays(1);
    }

    void sendForDays(int days) {
        LocalDate eventDate = LocalDate.now().plusDays(days);
        Stream.concat(
                bookingRepository.findByStatusAndEventDate(VendorServiceBookingStatus.CONFIRMED, eventDate).stream(),
                bookingRepository.findByStatusAndEventDate(VendorServiceBookingStatus.IN_PROGRESS, eventDate).stream())
                .filter(booking -> days == 7
                        ? booking.getReminder7dSentAt() == null
                        : booking.getReminder1dSentAt() == null)
                .forEach(booking -> sendReminder(booking, days));
    }

    private void sendReminder(VendorServiceBooking booking, int days) {
        Instant now = Instant.now();
        String timing = days == 1 ? "tomorrow" : "in " + days + " days";
        String customerMessage = "Your " + booking.getService() + " with "
                + booking.getVendor().getBusinessName() + " is " + timing + ".";
        String vendorMessage = booking.getService() + " for " + booking.getCustomer().getFullName()
                + " is " + timing + ".";

        notificationService.notifyUser(
                booking.getCustomer(),
                NotificationType.BOOKING,
                "Event reminder",
                customerMessage,
                "/customer?tab=bookings");
        notificationService.notifyUser(
                booking.getVendor().getUser(),
                NotificationType.BOOKING,
                "Upcoming booking",
                vendorMessage,
                "/vendor?tab=bookings");

        if (days == 7) booking.setReminder7dSentAt(now);
        else booking.setReminder1dSentAt(now);
        bookingRepository.save(booking);

        VendorBookingTimeline timeline = new VendorBookingTimeline();
        timeline.setBooking(booking);
        timeline.setEventType("REMINDER_SENT");
        timeline.setActorRole("SYSTEM");
        timeline.setMessage(days + "-day event reminder sent to customer and vendor");
        timelineRepository.save(timeline);

        auditService.record(new AuditCommand(
                null,
                "SYSTEM",
                AuditAction.VENDOR_BOOKING_REMINDER_SENT,
                "VENDOR_SERVICE_BOOKING",
                String.valueOf(booking.getId()),
                days + "-day vendor booking reminder sent",
                Map.of(),
                Map.of(),
                Map.of("daysBeforeEvent", days)));
    }
}
