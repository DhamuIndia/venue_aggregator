package com.staminal.venue.enquiries;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.staminal.venue.enums.EnquiryStatus;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.notifications.NotificationService;
import com.staminal.venue.notifications.NotificationType;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

class ApplicationVenueEnquiryNotificationsTest {
    @Test void notificationsUseOnlyActiveAdminRoleAndProfileRecipientsAndNoOwner() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class); UserRepository users = mock(UserRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        User customer = new User(); customer.setId(101L); User admin = new User(); admin.setId(8L);
        Halls hall = new Halls(); hall.setName("Team-managed Venue");
        Enquiry enquiry = new Enquiry(); enquiry.setCustomer(customer); enquiry.setHall(hall);
        enquiry.setCustomerPhone("private phone"); enquiry.setCustomerEmail("private email");
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenAnswer(invocation -> {
            assertThat((String) invocation.getArgument(0)).contains("upper(a.status) = 'ACTIVE'", "upper(u.status) = 'ACTIVE'", "'ADMIN', 'SUPER_ADMIN'", "a.email = u.email");
            return List.of(8L);
        });
        when(users.findById(8L)).thenReturn(Optional.of(admin));
        new ApplicationVenueEnquiryNotifications(jdbc, users, notifications).created(enquiry);
        verify(notifications).notifyUser(eq(customer), eq(NotificationType.ENQUIRY), eq("Availability request submitted"),
                contains("VenueMart team. Availability and pricing are not confirmed."), eq("/customer?tab=enquiries"));
        verify(notifications).notifyUser(eq(admin), eq(NotificationType.ENQUIRY), eq("New venue availability request"),
                argThat(message -> !message.contains("private phone") && !message.contains("private email")), eq("/admin?tab=application-enquiries"));
        verifyNoMoreInteractions(notifications);
    }

    @Test void teamResponseNeverNotifiesBookingsOrSuggestsConfirmation() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class); UserRepository users = mock(UserRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        User customer = new User(); Enquiry enquiry = new Enquiry(); enquiry.setCustomer(customer); enquiry.setStatus(EnquiryStatus.CONTACTED);
        ApplicationVenueEnquiryNotifications service = new ApplicationVenueEnquiryNotifications(jdbc, users, notifications);
        service.updated(enquiry); enquiry.setStatus(EnquiryStatus.CLOSED); service.updated(enquiry);
        verify(notifications, times(2)).notifyUser(eq(customer), eq(NotificationType.ENQUIRY), eq("VenueMart team updated your request"),
                contains("This is not a booking confirmation."), eq("/customer?tab=enquiries"));
        verifyNoInteractions(jdbc, users);
    }
}
