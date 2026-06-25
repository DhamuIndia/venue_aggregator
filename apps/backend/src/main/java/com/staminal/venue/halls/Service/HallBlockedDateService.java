package com.staminal.venue.halls.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    public BlockedDateResponse create(Long hallId, CreateBlockedDateRequest request) {

        Halls hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new RuntimeException("Hall not found"));

        HallBlockedDate blockedDate = new HallBlockedDate();

        blockedDate.setHallId(hall);
        blockedDate.setEventDate(request.getEventDate());
        blockedDate.setSlotType(request.getSlotType());
        blockedDate.setReason(request.getReason());
        blockedDate.setCreatedAt(LocalDateTime.now());
        hallBlockedDateRepository.save(blockedDate);

        return map(blockedDate);
    }

    public List<BlockedDateResponse> getByHall(Long hallId) {
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

    public void delete(Long hallId, Long blockId) {

        HallBlockedDate blockedDate = hallBlockedDateRepository.findById(blockId)
                .orElseThrow(() -> new RuntimeException("Blocked date not found"));

        if (blockedDate.getHallId().getId() != hallId) {
            throw new RuntimeException("Blocked date does not belong to this hall");
        }

        hallBlockedDateRepository.delete(blockedDate);
    }
}