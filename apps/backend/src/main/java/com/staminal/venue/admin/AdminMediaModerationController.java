package com.staminal.venue.admin;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/media")
@RequiredArgsConstructor
public class AdminMediaModerationController {

    private final AdminMediaModerationService adminMediaModerationService;

    @GetMapping("/pending")
    public List<Map<String, Object>> pendingMedia() {
        return adminMediaModerationService.pendingMedia();
    }

    @PatchMapping("/{type}/{mediaId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable String type, @PathVariable Long mediaId) {
        adminMediaModerationService.approve(type, mediaId);
    }

    @PatchMapping("/{type}/{mediaId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable String type, @PathVariable Long mediaId) {
        adminMediaModerationService.reject(type, mediaId);
    }
}
