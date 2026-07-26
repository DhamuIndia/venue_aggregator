package com.staminal.venue.notifications.queue;

import java.time.Instant;
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
            where job.status = :queuedStatus
               or (
                    job.status = :failedStatus
                    and job.failureTemporary = true
                    and job.nextRetryAt <= :now
                    and job.attemptCount < :maxAttempts
               )
            order by coalesce(job.nextRetryAt, job.queuedAt) asc, job.id asc
            """)
    List<LeadNotificationJob> findReadyForDispatch(
            @Param("queuedStatus") LeadNotificationJobStatus queuedStatus,
            @Param("failedStatus") LeadNotificationJobStatus failedStatus,
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts,
            Pageable pageable);

    Optional<LeadNotificationJob> findByProviderMessageId(String providerMessageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from LeadNotificationJob job where job.id = :id")
    Optional<LeadNotificationJob> findByIdForUpdate(@Param("id") Long id);
}
