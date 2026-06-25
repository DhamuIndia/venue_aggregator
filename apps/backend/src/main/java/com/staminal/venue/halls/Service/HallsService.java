package com.staminal.venue.halls.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.halls.Dto.CreateHallRequest;
import com.staminal.venue.halls.Dto.HallResponse;
import com.staminal.venue.halls.Dto.UpdateHallRequest;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

@Service
public class HallsService {

    @Autowired
    private HallRepository hallRepository;
    @Autowired
    private UserRepository userRepository;

    public HallResponse createHall(CreateHallRequest request, Authentication authentication) {

        Halls hall = new Halls();

        hall.setName(request.getName());
        hall.setDescription(request.getDescription());
        hall.setAddressLine(request.getAddressLine());
        hall.setCity(request.getCity());
        hall.setArea(request.getArea());
        hall.setPincode(request.getPincode());
        hall.setLatitude(request.getLatitude());
        hall.setLongitude(request.getLongitude());
        hall.setCapacityMin(request.getCapacityMin());
        hall.setCapacityMax(request.getCapacityMax());
        hall.setFloors(request.getFloors());
        hall.setAcAvailable(request.getAcAvailable());
        hall.setHallType(request.getHallType());
        hall.setRooms(request.getRooms());
        hall.setCarParking(request.getCarParking());
        hall.setBikeParking(request.getBikeParking());
        hall.setDiningAvailable(request.getDiningAvailable());
        hall.setDiningCapacity(request.getDiningCapacity());
        hall.setGeneratorAvailable(request.getGeneratorAvailable());
        hall.setLiftAvailable(request.getLiftAvailable());
        hall.setContactNumber(request.getContactNumber());
        hall.setWhatsappNumber(request.getWhatsappNumber());
        hall.setCoverImageUrl(request.getCoverImageUrl());
        hall.setRatings(0.0);
        hall.setStatus(HallStatus.DRAFT);
        hall.setCreatedAt(LocalDateTime.now());
        hall.setUpdatedAt(LocalDateTime.now());
        hall.setBridalRoomAvailable(request.getBridalRoomAvailable());
        hall.setCateringKitchenAvailable(request.getCateringKitchenAvailable());
        hall.setMorningAmount(request.getMorningAmount());
        hall.setEveningAmount(request.getEveningAmount());
        hall.setFullDayAmount(request.getFullDayAmount());

        Long userId = Long.valueOf(authentication.getName());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        hall.setOwnerUserId(user);
        hall.setOwnerName(user.getFullName());
        hallRepository.save(hall);

        return mapToResponse(hall);
    }

    private HallResponse mapToResponse(Halls hall) {

        HallResponse response = new HallResponse();

        response.setAddressLine(hall.getAddressLine());
        response.setPincode(hall.getPincode());

        response.setLatitude(hall.getLatitude());
        response.setLongitude(hall.getLongitude());

        response.setCapacityMin(hall.getCapacityMin());
        response.setCapacityMax(hall.getCapacityMax());

        response.setFloors(hall.getFloors());

        response.setRooms(hall.getRooms());

        response.setContactNumber(hall.getContactNumber());
        response.setWhatsappNumber(hall.getWhatsappNumber());

        response.setOwnerName(hall.getOwnerName());

        response.setDiningCapacity(hall.getDiningCapacity());

        response.setBridalRoomAvailable(hall.getBridalRoomAvailable());
        response.setCateringKitchenAvailable(hall.getCateringKitchenAvailable());
        response.setMorningAmount(hall.getMorningAmount());
        response.setEveningAmount(hall.getEveningAmount());
        response.setFullDayAmount(hall.getFullDayAmount());
        response.setApprovedAt(hall.getApprovedAt());

        response.setId(hall.getId());

        response.setName(hall.getName());

        response.setDescription(hall.getDescription());

        response.setCoverImageUrl(hall.getCoverImageUrl());

        response.setCity(hall.getCity());

        response.setArea(hall.getArea());

        response.setHallType(hall.getHallType());

        response.setRatings(hall.getRatings());

        response.setStatus(hall.getStatus().name());

        response.setRejectionReason(hall.getRejectionReason());

        if (hall.getApprovedBy() != null) {
            response.setApprovedBy(hall.getApprovedBy().getId());
        }

        return response;
    }

    public List<HallResponse> getMyHalls(Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());

        List<Halls> halls = hallRepository.findByOwnerUserId_Id(userId);

        return halls.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public HallResponse getHall(Long hallId, Authentication authentication) {

        Halls hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new RuntimeException("Hall not found"));

        Long userId = Long.valueOf(authentication.getName());
        if (hall.getOwnerUserId() == null ||
                !hall.getOwnerUserId().getId().equals(userId)) {

            throw new RuntimeException("You are not allowed to access this hall");
        }

        return mapToResponse(hall);
    }

    public HallResponse updateHall(
            Long hallId,
            UpdateHallRequest request,
            Authentication authentication) {

        Halls hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new RuntimeException("Hall not found"));

        Long userId = Long.valueOf(authentication.getName());

        if (hall.getOwnerUserId() == null ||
                !hall.getOwnerUserId().getId().equals(userId)) {

            throw new RuntimeException("You are not allowed to update this hall");
        }

        hall.setName(request.getName());
        hall.setDescription(request.getDescription());
        hall.setAddressLine(request.getAddressLine());
        hall.setCity(request.getCity());
        hall.setArea(request.getArea());
        hall.setPincode(request.getPincode());
        hall.setLatitude(request.getLatitude());
        hall.setLongitude(request.getLongitude());
        hall.setCapacityMin(request.getCapacityMin());
        hall.setCapacityMax(request.getCapacityMax());
        hall.setFloors(request.getFloors());
        hall.setAcAvailable(request.getAcAvailable());
        hall.setHallType(request.getHallType());
        hall.setRooms(request.getRooms());
        hall.setCarParking(request.getCarParking());
        hall.setBikeParking(request.getBikeParking());
        hall.setDiningAvailable(request.getDiningAvailable());
        hall.setDiningCapacity(request.getDiningCapacity());
        hall.setGeneratorAvailable(request.getGeneratorAvailable());
        hall.setLiftAvailable(request.getLiftAvailable());
        hall.setContactNumber(request.getContactNumber());
        hall.setWhatsappNumber(request.getWhatsappNumber());
        hall.setCoverImageUrl(request.getCoverImageUrl());

        hall.setBridalRoomAvailable(request.getBridalRoomAvailable());
        hall.setCateringKitchenAvailable(request.getCateringKitchenAvailable());
        hall.setMorningAmount(request.getMorningAmount());
        hall.setEveningAmount(request.getEveningAmount());
        hall.setFullDayAmount(request.getFullDayAmount());

        hall.setUpdatedAt(LocalDateTime.now());

        hallRepository.save(hall);

        return mapToResponse(hall);
    }

    public HallResponse submitHall(
            Long hallId,
            Authentication authentication) {

        Halls hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new RuntimeException("Hall not found"));

        Long userId = Long.valueOf(authentication.getName());

        if (hall.getOwnerUserId() == null ||
                !hall.getOwnerUserId().getId().equals(userId)) {

            throw new RuntimeException("You are not allowed to submit this hall");
        }

        hall.setStatus(HallStatus.PENDING_APPROVAL);
        hall.setUpdatedAt(LocalDateTime.now());

        hallRepository.save(hall);

        return mapToResponse(hall);
    }
}