package com.staminal.venue.admin.notifications;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.admin.notifications.AdminLeadNotificationMonitoringResponse.ManualRetry;
import com.staminal.venue.admin.notifications.AdminLeadNotificationMonitoringResponse.RequirementDetail;
import com.staminal.venue.admin.notifications.AdminLeadNotificationMonitoringResponse.RequirementList;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminLeadNotificationMonitoringController {

    private final AdminLeadNotificationMonitoringService monitoringService;

    @GetMapping("/requirements/notification-monitoring")
    public RequirementList getRequirements(Authentication authentication) {
        return monitoringService.getRequirements(authentication);
    }

    @GetMapping("/requirements/{requirementId}/notification-monitoring")
    public RequirementDetail getRequirement(
            @PathVariable Long requirementId,
            Authentication authentication) {
        return monitoringService.getRequirement(requirementId, authentication);
    }

    @PostMapping("/notification-jobs/{jobId}/retry")
    public ManualRetry scheduleManualRetry(
            @PathVariable Long jobId,
            Authentication authentication) {
        return monitoringService.scheduleManualRetry(jobId, authentication);
    }
}
