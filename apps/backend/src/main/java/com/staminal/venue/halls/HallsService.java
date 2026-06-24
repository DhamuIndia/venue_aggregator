package com.staminal.venue.halls;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.staminal.venue.enums.HallStatus;
import com.staminal.venue.halls.Dto.CreateHallRequest;
import com.staminal.venue.halls.Dto.HallResponse;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.users.Entity.User;
import com.staminal.venue.users.Repository.UserRepository;

@Service
public class HallsService {

    @Autowired
    private HallRepository hallRepository;
    @Autowired
    private UserRepository userRepository;

    public HallResponse createHall(CreateHallRequest request) {

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
        hall.setAmount(request.getAmount());
        hall.setContactNumber(request.getContactNumber());
        hall.setWhatsappNumber(request.getWhatsappNumber());
        hall.setCoverImageUrl(request.getCoverImageUrl());
        hall.setRatings(0.0);
        hall.setStatus(HallStatus.PENDING);
        hall.setCreatedAt(LocalDateTime.now());
        hall.setUpdatedAt(LocalDateTime.now());
        User user = userRepository.findById(request.getOwnerUserId())
                .orElseThrow();

        hall.setOwnerUserId(user);
        hall.setOwnerName(user.getFullName());
        hallRepository.save(hall);

        return mapToResponse(hall);
    }

    private HallResponse mapToResponse(Halls hall) {

        HallResponse response = new HallResponse();

        response.setId(hall.getId());
        response.setName(hall.getName());
        response.setDescription(hall.getDescription());
        response.setCoverImageUrl(hall.getCoverImageUrl());
        response.setCity(hall.getCity());
        response.setArea(hall.getArea());
        response.setCapacityMax(hall.getCapacityMax());
        response.setAmount(hall.getAmount());
        response.setHallType(hall.getHallType());
        response.setRatings(hall.getRatings());
        response.setAcAvailable(hall.getAcAvailable());
        response.setCarParking(hall.getCarParking());
        response.setBikeParking(hall.getBikeParking());
        response.setDiningAvailable(hall.getDiningAvailable());
        response.setRooms(hall.getRooms());
        response.setGeneratorAvailable(hall.getGeneratorAvailable());
        response.setLiftAvailable(hall.getLiftAvailable());
        response.setStatus(hall.getStatus().name());

        return response;
    }
}