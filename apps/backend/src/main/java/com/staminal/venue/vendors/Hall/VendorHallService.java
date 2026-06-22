package com.staminal.venue.vendors.Hall;

import org.springframework.stereotype.Service;

import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorHallService {

    private final VendorHallRepository vendorHallRepository;
    private final VendorRepository vendorRepository;

    public VendorHallResponse createVendorHall(
            CreateVendorHallRequest request) {

        Vendors vendor = vendorRepository.findById(
                request.getVendorId())
                .orElseThrow(() ->
                        new RuntimeException("Vendor not found"));

        VendorHallDetails hall = new VendorHallDetails();

        hall.setVendor(vendor);
        hall.setCapacityMin(request.getCapacityMin());
        hall.setCapacityMax(request.getCapacityMax());
        hall.setFloors(request.getFloors());
        hall.setRooms(request.getRooms());
        hall.setHallType(request.getHallType());
        hall.setAcAvailable(request.getAcAvailable());
        hall.setLiftAvailable(request.getLiftAvailable());
        hall.setGeneratorAvailable(request.getGeneratorAvailable());
        hall.setCarParking(request.getCarParking());
        hall.setBikeParking(request.getBikeParking());
        hall.setDiningAvailable(request.getDiningAvailable());
        hall.setDiningCapacity(request.getDiningCapacity());
        hall.setAmount(request.getAmount());

        VendorHallDetails saved =
                vendorHallRepository.save(hall);

        return mapToResponse(saved);
    }

    public VendorHallResponse getByVendorId(Long vendorId) {

        VendorHallDetails hall =
                vendorHallRepository.findByVendorId(vendorId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Hall details not found"));

        return mapToResponse(hall);
    }

    private VendorHallResponse mapToResponse(
            VendorHallDetails hall) {

        VendorHallResponse response =
                new VendorHallResponse();

        response.setId(hall.getId());
        response.setVendorId(hall.getVendor().getId());

        response.setCapacityMin(hall.getCapacityMin());
        response.setCapacityMax(hall.getCapacityMax());
        response.setFloors(hall.getFloors());
        response.setRooms(hall.getRooms());

        response.setHallType(hall.getHallType());

        response.setAcAvailable(
                hall.getAcAvailable());

        response.setLiftAvailable(
                hall.getLiftAvailable());

        response.setGeneratorAvailable(
                hall.getGeneratorAvailable());

        response.setCarParking(
                hall.getCarParking());

        response.setBikeParking(
                hall.getBikeParking());

        response.setDiningAvailable(
                hall.getDiningAvailable());

        response.setDiningCapacity(
                hall.getDiningCapacity());

        response.setAmount(
                hall.getAmount());

        return response;
    }
}