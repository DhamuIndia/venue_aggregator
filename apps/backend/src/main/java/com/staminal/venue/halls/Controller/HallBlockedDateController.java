package com.staminal.venue.halls.Controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.halls.Dto.BlockedDateResponse;
import com.staminal.venue.halls.Dto.CreateBlockedDateRequest;
import com.staminal.venue.halls.Service.HallBlockedDateService;

@RestController
@RequestMapping("/owner/halls")
public class HallBlockedDateController {

    @Autowired
    private HallBlockedDateService service;

    @PostMapping("/{hallId}/blocked-dates")
    public BlockedDateResponse create(@PathVariable Long hallId, @RequestBody CreateBlockedDateRequest request) {
        return service.create(hallId, request);
    }

    @GetMapping("/{hallId}/blocked-dates")
    public List<BlockedDateResponse> getByHall(@PathVariable Long hallId) {
        return service.getByHall(hallId);
    }

    @DeleteMapping("/{hallId}/blocked-dates/{blockId}")
    public void delete(@PathVariable Long hallId, @PathVariable Long blockId) {
        service.delete(hallId, blockId);
    }
}
