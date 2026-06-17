package com.staminal.venue.vendors.Photography;

import org.springframework.stereotype.Service;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorPhotographyService {

    private final VendorPhotographyRepository vendorPhotographyRepository;
    private final VendorRepository vendorRepository;

    public VendorPhotographyResponse createPhotography(
            CreateVendorPhotographyRequest request) {

        Vendors vendor = vendorRepository.findById(
                request.getVendorId())
                .orElseThrow(() ->
                        new RuntimeException("Vendor not found"));

        VendorPhotographyDetails photography =
                new VendorPhotographyDetails();

        photography.setVendor(vendor);
        photography.setExperienceYears(
                request.getExperienceYears());
        photography.setCandidPhotography(
                request.getCandidPhotography());
        photography.setVideographyAvailable(
                request.getVideographyAvailable());
        photography.setDroneAvailable(
                request.getDroneAvailable());
        photography.setAlbumIncluded(
                request.getAlbumIncluded());
        photography.setStartingPrice(
                request.getStartingPrice());

        VendorPhotographyDetails saved =
                vendorPhotographyRepository.save(
                        photography);

        return mapToResponse(saved);
    }

    public VendorPhotographyResponse getPhotography(
            Long vendorId) {

        VendorPhotographyDetails photography =
                vendorPhotographyRepository
                        .findByVendorId(vendorId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Photography details not found"));

        return mapToResponse(photography);
    }

    private VendorPhotographyResponse mapToResponse(
            VendorPhotographyDetails photography) {

        VendorPhotographyResponse response =
                new VendorPhotographyResponse();

        response.setId(photography.getId());
        response.setVendorId(
                photography.getVendor().getId());
        response.setExperienceYears(
                photography.getExperienceYears());
        response.setCandidPhotography(
                photography.getCandidPhotography());
        response.setVideographyAvailable(
                photography.getVideographyAvailable());
        response.setDroneAvailable(
                photography.getDroneAvailable());
        response.setAlbumIncluded(
                photography.getAlbumIncluded());
        response.setStartingPrice(
                photography.getStartingPrice());

        return response;
    }
}
