package com.staminal.venue.customer.savedhalls;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.customer.savedhalls.dto.CustomerSavedHallsResponse;
import com.staminal.venue.halls.Dto.HallResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/customer/saved-halls")
@RequiredArgsConstructor
public class CustomerSavedHallController {

    private final CustomerSavedHallService savedHallService;

    @GetMapping
    public CustomerSavedHallsResponse getSavedHalls(Authentication authentication) {
        return savedHallService.getSavedHalls(authentication);
    }

    @PutMapping("/{hallId}")
    public HallResponse saveHall(
            @PathVariable String hallId,
            Authentication authentication) {
        return savedHallService.saveHall(hallId, authentication);
    }

    @DeleteMapping("/{hallId}")
    public ResponseEntity<Void> removeHall(
            @PathVariable String hallId,
            Authentication authentication) {
        savedHallService.removeHall(hallId, authentication);
        return ResponseEntity.noContent().build();
    }
}
