package com.staminal.venue.vendors.Decoration;

import org.springframework.stereotype.Service;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorDecorationService {

    private final VendorDecorationRepository vendorDecorationRepository;
    private final VendorRepository vendorRepository;

    public VendorDecorationResponse createDecoration(
            CreateVendorDecorationRequest request) {

        Vendors vendor = vendorRepository.findById(
                request.getVendorId())
                .orElseThrow(() ->
                        new RuntimeException("Vendor not found"));

        VendorDecorationDetails decoration =
                new VendorDecorationDetails();

        decoration.setVendor(vendor);
        decoration.setExperienceYears(
                request.getExperienceYears());

        decoration.setFlowerDecorationAvailable(
                request.getFlowerDecorationAvailable());

        decoration.setBalloonDecorationAvailable(
                request.getBalloonDecorationAvailable());

        decoration.setStageDecorationAvailable(
                request.getStageDecorationAvailable());

        decoration.setThemeDecorationAvailable(
                request.getThemeDecorationAvailable());

        decoration.setStartingPrice(
                request.getStartingPrice());

        VendorDecorationDetails savedDecoration =
                vendorDecorationRepository.save(decoration);

        VendorDecorationResponse response =
                new VendorDecorationResponse();

        response.setId(savedDecoration.getId());

        response.setVendorId(
                savedDecoration.getVendor().getId());

        response.setExperienceYears(
                savedDecoration.getExperienceYears());

        response.setFlowerDecorationAvailable(
                savedDecoration.isFlowerDecorationAvailable());

        response.setBalloonDecorationAvailable(
                savedDecoration.isBalloonDecorationAvailable());

        response.setStageDecorationAvailable(
                savedDecoration.isStageDecorationAvailable());

        response.setThemeDecorationAvailable(
                savedDecoration.isThemeDecorationAvailable());

        response.setStartingPrice(
                savedDecoration.getStartingPrice());

        return response;
    }

    public VendorDecorationResponse getDecorationByVendorId(
            Long vendorId) {

        VendorDecorationDetails decoration =
                vendorDecorationRepository
                        .findByVendorId(vendorId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Decoration details not found"));

        VendorDecorationResponse response =
                new VendorDecorationResponse();

        response.setId(decoration.getId());

        response.setVendorId(
                decoration.getVendor().getId());

        response.setExperienceYears(
                decoration.getExperienceYears());

        response.setFlowerDecorationAvailable(
                decoration.isFlowerDecorationAvailable());

        response.setBalloonDecorationAvailable(
                decoration.isBalloonDecorationAvailable());

        response.setStageDecorationAvailable(
                decoration.isStageDecorationAvailable());

        response.setThemeDecorationAvailable(
                decoration.isThemeDecorationAvailable());

        response.setStartingPrice(
                decoration.getStartingPrice());

        return response;
    }
}
