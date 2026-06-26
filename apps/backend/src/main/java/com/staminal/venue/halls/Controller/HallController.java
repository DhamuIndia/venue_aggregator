package com.staminal.venue.halls.Controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.halls.Dto.CreateHallRequest;
import com.staminal.venue.halls.Dto.HallListResponse;
import com.staminal.venue.halls.Dto.HallResponse;
import com.staminal.venue.halls.Dto.UpdateHallRequest;
import com.staminal.venue.halls.Service.HallsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class HallController {

    private final HallsService hallsService;

    @GetMapping("/public/halls")
    public HallListResponse searchPublicHalls(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String venueType,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return hallsService.searchPublicHalls(q, city, area, venueType, minCapacity, maxPrice, sort, page, size);
    }

    @GetMapping("/public/halls/{hallId}")
    public HallResponse getPublicHall(@PathVariable String hallId) {
        return hallsService.getPublicHall(hallId);
    }

    @PostMapping("/owner/halls")
    public HallResponse createHall(
            @RequestBody CreateHallRequest request, Authentication authentication) {
        return hallsService.createHall(request, authentication);
    }

    @GetMapping("/owner/halls")
    public List<HallResponse> getMyHalls(Authentication authentication) {
        return hallsService.getMyHalls(authentication);
    }

    @GetMapping("/owner/halls/{hallId}")
    public HallResponse getHall(@PathVariable String hallId, Authentication authentication) {
        return hallsService.getHall(hallId, authentication);
    }

    @PutMapping("/owner/halls/{hallId}")
    public HallResponse updateHall(
            @PathVariable String hallId,
            @RequestBody UpdateHallRequest request,
            Authentication authentication) {

        return hallsService.updateHall(hallId, request, authentication);
    }

    @PostMapping("/owner/halls/{hallId}/submit")
    public HallResponse submitHall(
            @PathVariable String hallId,
            Authentication authentication) {

        return hallsService.submitHall(hallId, authentication);
    }
}
