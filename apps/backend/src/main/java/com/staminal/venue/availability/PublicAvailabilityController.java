package com.staminal.venue.availability;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.availability.Dto.AvailabilityResponse;

@RestController
@RequestMapping("/v1/public/halls")
public class PublicAvailabilityController {

    private final AvailabilityService availabilityService;

    public PublicAvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/{hallId}/availability")
    public AvailabilityResponse getPublicAvailability(@PathVariable String hallId) {
        return availabilityService.getPublicAvailability(hallId);
    }
}
