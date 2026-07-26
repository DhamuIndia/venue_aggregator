package com.staminal.venue.notifications.queue;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface WhatsAppNotificationAttemptRepository
        extends JpaRepository<WhatsAppNotificationAttempt, Long> {

    Optional<WhatsAppNotificationAttempt> findByIdAndStatus(
            Long id,
            WhatsAppNotificationAttemptStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WhatsAppNotificationAttempt> findByProviderMessageId(String providerMessageId);
}
