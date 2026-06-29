package com.staminal.venue.availability;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.availability.Dto.AvailabilityResponse;

@RestController
@RequestMapping("/v1/owner/halls")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/{hallId}/availability")
    public AvailabilityResponse getAvailability(
            @PathVariable Long hallId, Authentication authentication) {

        return availabilityService.getAvailability(hallId, authentication);
    }

}