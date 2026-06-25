package com.staminal.venue.halls.Controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.halls.Dto.CreateHallMediaRequest;
import com.staminal.venue.halls.Dto.HallMediaResponse;
import com.staminal.venue.halls.Service.HallMediaService;

@RestController
@RequestMapping("/owner/halls")
public class HallMediaController {

    @Autowired
    private HallMediaService hallMediaService;

    @PostMapping("/{hallId}/media")
    public HallMediaResponse create(@PathVariable Long hallId, @RequestBody CreateHallMediaRequest request) {
        return hallMediaService.create(hallId, request);
    }

    @GetMapping("/{hallId}/media")
    public List<HallMediaResponse> getByHall(@PathVariable Long hallId) {
        return hallMediaService.getByHall(hallId);
    }

    @PatchMapping("/{hallId}/media/{mediaId}")
    public HallMediaResponse updateHallMedia(
            @PathVariable Long hallId,
            @PathVariable Long mediaId,
            @RequestBody CreateHallMediaRequest request) {

        return hallMediaService.updateHallMedia(hallId, mediaId, request);
    }

    @DeleteMapping("/{hallId}/media/{mediaId}")
    public void deleteHallMedia(
            @PathVariable Long hallId,
            @PathVariable Long mediaId) {

        hallMediaService.deleteHallMedia(hallId, mediaId);
    }
}
