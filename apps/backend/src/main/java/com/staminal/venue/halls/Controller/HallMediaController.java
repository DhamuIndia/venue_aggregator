package com.staminal.venue.halls.Controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/v1/owner/halls")
public class HallMediaController {

    @Autowired
    private HallMediaService hallMediaService;

    @PostMapping("/{hallId}/media")
    public HallMediaResponse create(
            @PathVariable Long hallId,
            @RequestBody CreateHallMediaRequest request,
            Authentication authentication) {
        return hallMediaService.create(hallId, request, authentication);
    }

    @GetMapping("/{hallId}/media")
    public List<HallMediaResponse> getByHall(
            @PathVariable Long hallId,
            Authentication authentication) {
        return hallMediaService.getByHall(hallId, authentication);
    }

    @PatchMapping("/{hallId}/media/{mediaId}")
    public HallMediaResponse updateHallMedia(
            @PathVariable Long hallId,
            @PathVariable Long mediaId,
            @RequestBody CreateHallMediaRequest request,
            Authentication authentication) {

        return hallMediaService.updateHallMedia(hallId, mediaId, request, authentication);
    }

    @DeleteMapping("/{hallId}/media/{mediaId}")
    public void deleteHallMedia(
            @PathVariable Long hallId,
            @PathVariable Long mediaId,
            Authentication authentication) {

        hallMediaService.deleteHallMedia(hallId, mediaId, authentication);
    }
}
