package com.staminal.venue.notifications.queue;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadNotificationEvaluationRepository
        extends JpaRepository<LeadNotificationEvaluation, Long> {

    Optional<LeadNotificationEvaluation>
            findByVendorLead_IdAndChannelAndNotificationType(
                    Long vendorLeadId,
                    NotificationChannel channel,
                    LeadNotificationType notificationType);

    List<LeadNotificationEvaluation>
            findByRequirement_IdOrderByEvaluatedAtAsc(Long requirementId);
}
