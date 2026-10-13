package com.staminal.venue.enquiries;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.notifications.NotificationType;
import com.staminal.venue.users.Repository.UserRepository;
import lombok.RequiredArgsConstructor;

/** Only persisted in-app notifications; deliberately independent of lead/WhatsApp queues. */
@Service
@RequiredArgsConstructor
public class ApplicationVenueEnquiryNotifications {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final NotificationService notifications;

    public void created(Enquiry enquiry) {
        notifications.notifyUser(enquiry.getCustomer(), NotificationType.ENQUIRY,
                "Availability request submitted",
                "Your request for " + hallName(enquiry) + " was sent to the VenueMart team. Availability and pricing are not confirmed.",
                "/customer?tab=enquiries");
        List<Long> recipients = jdbc.queryForList("""
                select distinct u.id from users u
                join admins a on a.email = u.email and upper(a.status) = 'ACTIVE'
                join user_roles ur on ur.user_id = u.id
                join roles r on r.id = ur.role_id and r.name in ('ADMIN', 'SUPER_ADMIN')
                where upper(u.status) = 'ACTIVE'
                order by u.id
                """, Long.class);
        for (Long recipientId : recipients) {
            users.findById(recipientId).ifPresent(recipient -> notifications.notifyUser(recipient,
                    NotificationType.ENQUIRY, "New venue availability request",
                    "A customer requested availability for " + hallName(enquiry) + ". Review this request in the VenueMart team queue.",
                    "/admin?tab=application-enquiries"));
        }
    }

    public void updated(Enquiry enquiry) {
        String action = enquiry.getStatus() == com.staminal.venue.enums.EnquiryStatus.CLOSED ? "closed" : "contacted you about";
        notifications.notifyUser(enquiry.getCustomer(), NotificationType.ENQUIRY,
                "VenueMart team updated your request",
                "The VenueMart team " + action + " your availability request for " + hallName(enquiry)
                        + ". View the team's response. This is not a booking confirmation.",
                "/customer?tab=enquiries");
    }

    private String hallName(Enquiry enquiry) {
        String name = enquiry.getHall() == null ? null : enquiry.getHall().getName();
        return name == null || name.isBlank() ? "the venue" : name;
    }
}
