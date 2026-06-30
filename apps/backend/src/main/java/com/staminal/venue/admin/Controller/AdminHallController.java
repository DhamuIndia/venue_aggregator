package com.staminal.venue.admin.Controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.staminal.venue.admin.Dto.AdminHallResponse;
import com.staminal.venue.admin.Dto.ReviewHallRequest;
import com.staminal.venue.admin.Service.AdminHallService;
import com.staminal.venue.enums.HallStatus;

@RestController
@RequestMapping("/v1/admin/hall")
public class AdminHallController {

    @Autowired
    private AdminHallService adminHallService;

    @GetMapping
    public List<AdminHallResponse> getHalls(@RequestParam HallStatus status) {
        return adminHallService.getHalls(status);
    }

    @PatchMapping("/{hallId}/review")
    public AdminHallResponse reviewHall(
            @PathVariable Long hallId,
            @RequestBody ReviewHallRequest request,
            Authentication authentication) {

        return adminHallService.reviewHall(
                hallId,
                request,
                authentication);
    }

}