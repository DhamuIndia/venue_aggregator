package com.staminal.venue.notifications;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(Authentication authentication) {
        User recipient = currentUser(authentication);
        List<NotificationResponse> items = notificationRepository
                .findByRecipient_IdOrderByCreatedAtDesc(recipient.getId())
                .stream()
                .map(this::toResponse)
                .toList();

        return new NotificationListResponse(
                items,
                notificationRepository.countByRecipient_IdAndReadAtIsNull(recipient.getId()));
    }

    public NotificationResponse markRead(String notificationId, Authentication authentication) {
        User recipient = currentUser(authentication);
        Notification notification = findOwnedNotification(notificationId, recipient.getId());
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification = notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    public NotificationListResponse markAllRead(Authentication authentication) {
        User recipient = currentUser(authentication);
        Instant now = Instant.now();
        List<Notification> unread = notificationRepository
                .findByRecipient_IdAndReadAtIsNullOrderByCreatedAtDesc(recipient.getId());
        unread.forEach(notification -> notification.setReadAt(now));
        if (!unread.isEmpty()) {
            notificationRepository.saveAll(unread);
        }
        return getNotifications(authentication);
    }

    public NotificationResponse notifyUser(
            User recipient,
            NotificationType type,
            String title,
            String message,
            String actionHref) {
        if (recipient == null || recipient.getId() == null) {
            return null;
        }

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type != null ? type : NotificationType.SYSTEM);
        notification.setTitle(required(title, "title"));
        notification.setMessage(required(message, "message"));
        notification.setActionHref(normalizeActionHref(actionHref));
        return toResponse(notificationRepository.save(notification));
    }

    private Notification findOwnedNotification(String notificationId, Long recipientId) {
        return notificationRepository.findByIdAndRecipient_Id(parseNotificationId(notificationId), recipientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        try {
            Long userId = Long.valueOf(authentication.getName());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session is invalid"));
            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is not active");
            }
            return user;
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session is invalid", exception);
        }
    }

    private Long parseNotificationId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.toUpperCase().startsWith("NOTIF-")) {
            normalized = normalized.substring(6);
        }
        try {
            return Long.valueOf(normalized);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid notification id", exception);
        }
    }

    private String normalizeActionHref(String actionHref) {
        if (actionHref == null || actionHref.isBlank()) {
            return null;
        }
        String trimmed = actionHref.trim();
        if (!trimmed.startsWith("/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notification action must be a frontend route");
        }
        return trimmed;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notification " + field + " is required");
        }
        return value.trim();
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                "NOTIF-" + String.format("%06d", notification.getId()),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.getActionHref());
    }
}
