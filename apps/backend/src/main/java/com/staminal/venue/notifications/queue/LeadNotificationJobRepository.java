package com.staminal.venue.notifications.queue;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface LeadNotificationJobRepository extends JpaRepository<LeadNotificationJob, Long> {

    boolean existsByVendorLead_IdAndChannelAndNotificationType(
            Long vendorLeadId,
            NotificationChannel channel,
            LeadNotificationType notificationType);

    Optional<LeadNotificationJob> findByIdAndStatus(
            Long id,
            LeadNotificationJobStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select job
            from LeadNotificationJob job
            where job.status = :status
            order by job.queuedAt asc, job.id asc
            """)
    List<LeadNotificationJob> findForDispatch(
            @Param("status") LeadNotificationJobStatus status,
            Pageable pageable);
}
