package com.staminal.venue.halls;

import java.util.List;

import org.springframework.stereotype.Service;

import com.staminal.venue.halls.Dto.CreateHallRequest;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HallsService {

    private final HallsRepository hallsRepository;

    public Halls createHall(CreateHallRequest request) {
        Halls hall = new Halls();

        hall.setOwnerUserId(request.getOwnerUserId());
        hall.setOwnerName(request.getOwnerName());
        hall.setHallName(request.getHallName());
        hall.setDescription(request.getDescription());
        hall.setAddressLine(request.getAddressLine());
        hall.setCity(request.getCity());
        hall.setArea(request.getArea());
        hall.setPincode(request.getPincode());
        hall.setCapacityMin(request.getCapacityMin());
        hall.setCapacityMax(request.getCapacityMax());
        hall.setContactNumber(request.getContactNumber());
        hall.setWhatsappNumber(request.getWhatsappNumber());
        hall.setAmount(request.getAmount());

        return hallsRepository.save(hall);
    }

    public List<Halls> getAllHalls() {
        return hallsRepository.findAll();
    }

    public Halls getById(long id) {
        return hallsRepository.findById(id).orElseThrow(() -> new RuntimeException("Hall Not Found!!"));
    }
}
