package com.staminal.venue.customer.savedhalls;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.customer.savedhalls.dto.CustomerSavedHallsResponse;
import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.enums.UserRole;
import com.staminal.venue.halls.Dto.HallResponse;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.halls.Service.HallsService;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class CustomerSavedHallService {

    private final CustomerSavedHallRepository savedHallRepository;
    private final HallRepository hallRepository;
    private final HallsService hallsService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CustomerSavedHallsResponse getSavedHalls(Authentication authentication) {
        User customer = currentUser(authentication);
        List<HallResponse> halls = savedHallRepository.findByCustomer_IdOrderByCreatedAtDesc(customer.getId())
                .stream()
                .map(CustomerSavedHall::getHall)
                .filter(hall -> hall.getStatus() == HallStatus.APPROVED)
                .map(hallsService::toPublicResponse)
                .toList();
        return new CustomerSavedHallsResponse(halls, halls, halls.size());
    }

    public HallResponse saveHall(String hallId, Authentication authentication) {
        User customer = currentUser(authentication);
        Halls hall = findApprovedHall(hallId);

        savedHallRepository.findByCustomer_IdAndHall_Id(customer.getId(), hall.getId())
                .orElseGet(() -> {
                    CustomerSavedHall savedHall = new CustomerSavedHall();
                    savedHall.setCustomer(customer);
                    savedHall.setHall(hall);
                    return savedHallRepository.save(savedHall);
                });

        return hallsService.toPublicResponse(hall);
    }

    public void removeHall(String hallId, Authentication authentication) {
        User customer = currentUser(authentication);
        Halls hall = findApprovedHall(hallId);

        savedHallRepository.findByCustomer_IdAndHall_Id(customer.getId(), hall.getId())
                .ifPresent(savedHallRepository::delete);
    }

    private Halls findApprovedHall(String hallId) {
        Long numericId = tryParseLong(hallId);
        Halls hall = numericId != null
                ? hallRepository.findById(numericId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"))
                : hallRepository.findByStatus(HallStatus.APPROVED)
                        .stream()
                        .filter(candidate -> slugify(candidate.getName()).equals(slugify(hallId)))
                        .findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"));

        if (hall.getStatus() != HallStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found");
        }
        return hall;
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!hasRole(authentication, UserRole.CUSTOMER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CUSTOMER role is required");
        }

        try {
            Long userId = Long.valueOf(authentication.getName());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session is invalid"));
            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is not active");
            }
            return user;
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session is invalid", exception);
        }
    }

    private boolean hasRole(Authentication authentication, UserRole role) {
        String authority = "ROLE_" + role.name();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private Long tryParseLong(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String slugify(String value) {
        return value == null
                ? ""
                : value.trim()
                        .toLowerCase()
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("(^-|-$)", "");
    }
}
