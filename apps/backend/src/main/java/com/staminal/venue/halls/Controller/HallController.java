package com.staminal.venue.halls.Controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.halls.Dto.CreateHallRequest;
import com.staminal.venue.halls.Dto.HallResponse;
import com.staminal.venue.halls.Dto.UpdateHallRequest;
import com.staminal.venue.halls.Service.HallsService;

@RestController
@RequestMapping("/v1/owner/halls")
public class HallController {

    @Autowired
    private HallsService hallsService;

    @PostMapping
    public HallResponse createHall(
            @RequestBody CreateHallRequest request, Authentication authentication) {
        System.out.println("HALL API HIT");
        System.out.println(authentication);
        System.out.println(authentication.getName());
        System.out.println(request.getName());
        return hallsService.createHall(request, authentication);
    }

    @GetMapping
    public List<HallResponse> getMyHalls(Authentication authentication) {
        return hallsService.getMyHalls(authentication);
    }

    @GetMapping("/{hallId}")
    public HallResponse getHall(@PathVariable String hallId, Authentication authentication) {

        List<HallResponse> halls = hallsService.getMyHalls(authentication);
        if (halls.isEmpty()) {
            throw new RuntimeException("No hall found");
        }

        return halls.get(0);
    }

    @PutMapping("/{hallId}")
    public HallResponse updateHall(
            @PathVariable String hallId,
            @RequestBody UpdateHallRequest request,
            Authentication authentication) {

        return hallsService.updateHall(hallId, request, authentication);
    }

    @PostMapping("/{hallId}/submit")
    public HallResponse submitHall(
            @PathVariable String hallId,
            Authentication authentication) {

        return hallsService.submitHall(hallId, authentication);
    }
}
