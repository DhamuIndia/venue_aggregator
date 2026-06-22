package com.staminal.venue.vendors.Catering;

import org.springframework.stereotype.Service;

import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorCateringService {

    private final VendorCateringRepository vendorCateringRepository;
    private final VendorRepository vendorRepository;

    public VendorCateringResponse create(
            CreateVendorCateringRequest request) {

        Vendors vendor = vendorRepository.findById(
                request.getVendorId())
                .orElseThrow(() ->
                        new RuntimeException("Vendor not found"));

        VendorCateringDetails details =
                new VendorCateringDetails();

        details.setVendor(vendor);
        details.setVegAvailable(
                request.getVegAvailable());
        details.setNonVegAvailable(
                request.getNonVegAvailable());
        details.setServiceType(
                request.getServiceType());
        details.setMinGuestCount(
                request.getMinGuestCount());
        details.setMaxGuestCount(
                request.getMaxGuestCount());
        details.setLiveCounterAvailable(
                request.getLiveCounterAvailable());
        details.setStartingPricePerPlate(
                request.getStartingPricePerPlate());

        VendorCateringDetails saved =
                vendorCateringRepository.save(details);

        VendorCateringResponse response =
                new VendorCateringResponse();

        response.setId(saved.getId());
        response.setVendorId(
                saved.getVendor().getId());
        response.setVegAvailable(
                saved.getVegAvailable());
        response.setNonVegAvailable(
                saved.getNonVegAvailable());
        response.setServiceType(
                saved.getServiceType());
        response.setMinGuestCount(
                saved.getMinGuestCount());
        response.setMaxGuestCount(
                saved.getMaxGuestCount());
        response.setLiveCounterAvailable(
                saved.getLiveCounterAvailable());
        response.setStartingPricePerPlate(
                saved.getStartingPricePerPlate());

        return response;
    }

    public VendorCateringResponse getByVendorId(
            Long vendorId) {

        VendorCateringDetails details =
                vendorCateringRepository
                        .findByVendorId(vendorId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Details not found"));

        VendorCateringResponse response =
                new VendorCateringResponse();

        response.setId(details.getId());
        response.setVendorId(
                details.getVendor().getId());
        response.setVegAvailable(
                details.getVegAvailable());
        response.setNonVegAvailable(
                details.getNonVegAvailable());
        response.setServiceType(
                details.getServiceType());
        response.setMinGuestCount(
                details.getMinGuestCount());
        response.setMaxGuestCount(
                details.getMaxGuestCount());
        response.setLiveCounterAvailable(
                details.getLiveCounterAvailable());
        response.setStartingPricePerPlate(
                details.getStartingPricePerPlate());

        return response;
    }
}