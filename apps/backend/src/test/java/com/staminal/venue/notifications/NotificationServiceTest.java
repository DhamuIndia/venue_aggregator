package com.staminal.venue.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, userRepository);
    }

    @Test
    void returnsCurrentUserNotificationFeed() {
        User user = user();
        Notification notification = notification(user);

        when(userRepository.findById(101L)).thenReturn(Optional.of(user));
        when(notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(101L)).thenReturn(List.of(notification));
        when(notificationRepository.countByRecipient_IdAndReadAtIsNull(101L)).thenReturn(1L);

        NotificationListResponse response = notificationService.getNotifications(auth(101L));

        assertThat(response.unreadCount()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).id()).isEqualTo("NOTIF-000501");
        assertThat(response.items().get(0).type()).isEqualTo(NotificationType.BOOKING);
    }

    @Test
    void markReadScopesNotificationToCurrentUser() {
        User user = user();
        Notification notification = notification(user);

        when(userRepository.findById(101L)).thenReturn(Optional.of(user));
        when(notificationRepository.findByIdAndRecipient_Id(501L, 101L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = notificationService.markRead("NOTIF-000501", auth(101L));

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());

        assertThat(notificationCaptor.getValue().getReadAt()).isNotNull();
        assertThat(response.readAt()).isNotNull();
    }

    @Test
    void invalidActionHrefIsRejected() {
        assertThatThrownBy(() -> notificationService.notifyUser(
                user(),
                NotificationType.SYSTEM,
                "System update",
                "Please review your account.",
                "https://external.example.com"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");
    }

    private Notification notification(User user) {
        Notification notification = new Notification();
        notification.setId(501L);
        notification.setRecipient(user);
        notification.setType(NotificationType.BOOKING);
        notification.setTitle("Booking confirmed");
        notification.setMessage("Emerald Convention Centre confirmed your booking.");
        notification.setActionHref("/customer?tab=bookings");
        notification.setCreatedAt(Instant.parse("2026-06-24T09:30:00Z"));
        return notification;
    }

    private User user() {
        User user = new User();
        user.setId(101L);
        user.setFullName("Priya Raman");
        user.setPhone("9876543210");
        user.setEmail("priya@example.com");
        user.setStatus("ACTIVE");
        return user;
    }

    private UsernamePasswordAuthenticationToken auth(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
    }
}
