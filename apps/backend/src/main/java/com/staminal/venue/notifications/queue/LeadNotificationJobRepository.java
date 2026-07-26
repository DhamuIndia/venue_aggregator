package com.staminal.venue.notifications.queue;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadNotificationJobRepository extends JpaRepository<LeadNotificationJob, Long> {

    boolean existsByVendorLead_IdAndChannelAndNotificationType(
            Long vendorLeadId,
            NotificationChannel channel,
            LeadNotificationType notificationType);
}
