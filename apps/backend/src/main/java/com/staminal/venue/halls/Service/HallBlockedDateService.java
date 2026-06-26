package com.staminal.venue.halls.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.halls.Dto.BlockedDateResponse;
import com.staminal.venue.halls.Dto.CreateBlockedDateRequest;
import com.staminal.venue.halls.Entity.HallBlockedDate;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallBlockedDateRepository;
import com.staminal.venue.halls.Repository.HallRepository;

@Service
public class HallBlockedDateService {

    @Autowired
    private HallRepository hallRepository;

    @Autowired
    private HallBlockedDateRepository hallBlockedDateRepository;

    public BlockedDateResponse create(Long hallId, CreateBlockedDateRequest request, Authentication authentication) {
        Halls hall = findOwnedHall(hallId, authentication);

        HallBlockedDate blockedDate = new HallBlockedDate();

        blockedDate.setHallId(hall);
        blockedDate.setEventDate(request.getEventDate());
        blockedDate.setSlotType(request.getSlotType());
        blockedDate.setReason(request.getReason());
        blockedDate.setCreatedAt(LocalDateTime.now());
        hallBlockedDateRepository.save(blockedDate);

        return map(blockedDate);
    }

    public List<BlockedDateResponse> getByHall(Long hallId, Authentication authentication) {
        findOwnedHall(hallId, authentication);
        return hallBlockedDateRepository.findByHallId_Id(hallId)
                .stream()
                .map(this::map)
                .toList();
    }

    private BlockedDateResponse map(
            HallBlockedDate blockedDate) {

        BlockedDateResponse response = new BlockedDateResponse();

        response.setId(blockedDate.getId());
        response.setHallId(blockedDate.getHallId().getId());
        response.setEventDate(blockedDate.getEventDate());
        response.setSlotType(blockedDate.getSlotType());
        response.setReason(blockedDate.getReason());

        return response;
    }

    public void delete(Long hallId, Long blockId, Authentication authentication) {
        findOwnedHall(hallId, authentication);

        HallBlockedDate blockedDate = hallBlockedDateRepository.findById(blockId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blocked date not found"));

        if (blockedDate.getHallId().getId() != hallId.longValue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Blocked date does not belong to this hall");
        }

        hallBlockedDateRepository.delete(blockedDate);
    }

    private Halls findOwnedHall(Long hallId, Authentication authentication) {
        Long userId = currentUserId(authentication);
        Halls hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"));
        if (hall.getOwnerUserId() == null || !hall.getOwnerUserId().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hall does not belong to this owner");
        }
        return hall;
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session is invalid", exception);
        }
    }
}
