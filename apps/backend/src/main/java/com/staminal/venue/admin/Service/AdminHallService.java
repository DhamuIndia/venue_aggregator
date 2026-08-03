package com.staminal.venue.admin.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.admin.Admin;
import com.staminal.venue.admin.AdminRepository;
import com.staminal.venue.admin.Dto.AdminHallResponse;
import com.staminal.venue.admin.Dto.ReviewHallRequest;
import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallRepository;

@Service
public class AdminHallService {

    @Autowired
    private HallRepository hallRepository;

    @Autowired
    private AdminRepository adminRepository;

    public List<AdminHallResponse> getHalls(HallStatus status) {

        List<Halls> halls = hallRepository.findByStatus(status);

        return halls.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private AdminHallResponse mapToResponse(Halls hall) {

        AdminHallResponse response = new AdminHallResponse();

        response.setId(hall.getId());

        response.setName(hall.getName());

        response.setOwnerName(hall.getOwnerName());

        if (hall.getOwnerUserId() != null) {
            response.setOwnerPhone(hall.getOwnerUserId().getPhone());
        }

        response.setLocation(hall.getArea() + ", " + hall.getCity());

        response.setVenueType(hall.getHallType());

        response.setCapacity(hall.getCapacityMax());

        response.setStartingPrice(hall.getFullDayAmount());

        response.setSubmittedAt(hall.getCreatedAt());

        response.setUpdatedAt(hall.getUpdatedAt());

        response.setImageUrl(hall.getCoverImageUrl());

        response.setStatus(hall.getStatus().name());

        return response;
    }

    public AdminHallResponse reviewHall(
            Long hallId,
            ReviewHallRequest request,
            Authentication authentication) {

        Halls hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new RuntimeException("Hall not found"));

        if (hall.getStatus() == HallStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hall is already approved.");
        }

        if (hall.getStatus() == HallStatus.REJECTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Hall is already rejected.");
        }

        String email = authentication.getName();

        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if ("APPROVED".equalsIgnoreCase(request.getDecision())) {

            hall.setStatus(HallStatus.APPROVED);
            hall.setApprovedBy(admin);
            hall.setApprovedAt(LocalDateTime.now());
            hall.setRejectionReason(null);

        } else if ("REJECTED".equalsIgnoreCase(request.getDecision())) {

            if (request.getReason() == null || request.getReason().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Rejection reason is required");
            }

            hall.setStatus(HallStatus.REJECTED);
            hall.setRejectionReason(request.getReason());
            hall.setApprovedBy(null);
            hall.setApprovedAt(null);

        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid decision");
        }

        hall.setUpdatedAt(LocalDateTime.now());

        hallRepository.save(hall);

        return mapToResponse(hall);
    }

}
