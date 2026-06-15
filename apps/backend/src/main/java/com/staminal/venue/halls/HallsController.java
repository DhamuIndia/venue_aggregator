package com.staminal.venue.halls;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.halls.Dto.CreateHallRequest;
import com.staminal.venue.halls.Entity.Halls;

import org.springframework.web.bind.annotation.RequestBody;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/halls")
@RequiredArgsConstructor
public class HallsController {

    private final HallsService hallsService;

    @PostMapping
    public Halls createHalls(@RequestBody CreateHallRequest hallRequest) {
        return hallsService.createHall(hallRequest);
    }

    @GetMapping
    public List<Halls> getAllHalls(){
        return hallsService.getAllHalls();
    }

    @GetMapping("/{id}")
    public Halls getHallById(@PathVariable long id){
        return hallsService.getById(id);
    }

}
