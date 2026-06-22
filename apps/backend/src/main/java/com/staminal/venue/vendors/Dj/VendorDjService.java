package com.staminal.venue.vendors.Dj;

import java.time.Instant;
import org.springframework.stereotype.Service;

import com.staminal.venue.enums.VendorStatus;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorDjService {

        private final VendorDjRepository vendorDjRepository;
        private final VendorRepository vendorRepository;

        public VendorDjResponse createDjDetails(
                        CreateVendorDjRequest request) {

                Vendors vendor = vendorRepository.findById(
                                request.getVendorId())
                                .orElseThrow(() -> new RuntimeException("Vendor not found"));

                VendorDjDetails djDetails = new VendorDjDetails();

                djDetails.setVendor(vendor);
                djDetails.setExperienceYears(request.getExperienceYears());
                djDetails.setSoundSystemAvailable(request.getSoundSystemAvailable());
                djDetails.setTravelDistanceKm(request.getTravelDistanceKm());
                djDetails.setStartingPrice(request.getStartingPrice());
                djDetails.setCreatedAt(Instant.now());
                djDetails.setUpdatedAt(Instant.now());
                djDetails.setStatus(VendorStatus.PENDING);
                djDetails.setRejectionReason(null);

                VendorDjDetails savedDj = vendorDjRepository.save(djDetails);

                return mapToResponse(savedDj);
        }

        public VendorDjResponse getDjDetails(Long vendorId) {

                VendorDjDetails djDetails = vendorDjRepository.findByVendor_Id(vendorId)
                                .orElseThrow(() -> new RuntimeException(
                                                "DJ Details not found"));

                return mapToResponse(djDetails);
        }

        private VendorDjResponse mapToResponse(
                        VendorDjDetails djDetails) {

                VendorDjResponse response = new VendorDjResponse();

                response.setId(djDetails.getId());
                response.setVendorId(djDetails.getVendor().getId());
                response.setExperienceYears(djDetails.getExperienceYears());
                response.setSoundSystemAvailable(djDetails.getSoundSystemAvailable());
                response.setTravelDistanceKm(djDetails.getTravelDistanceKm());
                response.setStartingPrice(djDetails.getStartingPrice());
                response.setStatus(djDetails.getStatus().name());
                response.setRejectionReason(djDetails.getRejectionReason());

                return response;
        }
}