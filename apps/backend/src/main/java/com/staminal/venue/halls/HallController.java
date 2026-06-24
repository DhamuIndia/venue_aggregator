package com.staminal.venue.halls;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.halls.Dto.CreateHallRequest;
import com.staminal.venue.halls.Dto.HallResponse;

import io.swagger.v3.oas.annotations.parameters.RequestBody;

@RestController
@RequestMapping("/v1/halls")
public class HallController {

    @Autowired
    private HallsService hallsService;

    @PostMapping
    public HallResponse createHall(
            @RequestBody CreateHallRequest request) {

        return hallsService.createHall(request);
    }
}
