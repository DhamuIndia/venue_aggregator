package com.staminal.venue.notifications;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public NotificationListResponse getNotifications(Authentication authentication) {
        return notificationService.getNotifications(authentication);
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markRead(
            @PathVariable String notificationId,
            Authentication authentication) {
        return notificationService.markRead(notificationId, authentication);
    }

    @PatchMapping("/read-all")
    public NotificationListResponse markAllRead(Authentication authentication) {
        return notificationService.markAllRead(authentication);
    }
}
