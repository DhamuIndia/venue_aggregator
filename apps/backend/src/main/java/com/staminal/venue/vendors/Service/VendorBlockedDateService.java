package com.staminal.venue.vendors.Service;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import com.staminal.venue.vendors.Dto.CreateVendorBlockedDateRequest;
import com.staminal.venue.vendors.Dto.VendorBlockedDateResponse;
import com.staminal.venue.vendors.Entity.VendorBlockedDate;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorBlockedDateRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorBlockedDateService {

        private final VendorBlockedDateRepository vendorBlockedDateRepository;
        private final VendorRepository vendorRepository;

        public VendorBlockedDateResponse createBlockedDate(
                        CreateVendorBlockedDateRequest request) {

                Vendors vendor = vendorRepository.findById(
                                request.getVendorId())
                                .orElseThrow(() -> new RuntimeException("Vendor not found"));

                VendorBlockedDate blockedDate = new VendorBlockedDate();

                blockedDate.setVendor(vendor);
                blockedDate.setEventDate(request.getEventDate());
                blockedDate.setSlotType(request.getSlotType());
                blockedDate.setReason(request.getReason());
                blockedDate.setCreatedAt(Instant.now());

                VendorBlockedDate savedBlockedDate = vendorBlockedDateRepository.save(blockedDate);

                VendorBlockedDateResponse response = new VendorBlockedDateResponse();

                response.setId(savedBlockedDate.getId());
                response.setEventDate(savedBlockedDate.getEventDate());
                response.setSlotType(savedBlockedDate.getSlotType());
                response.setReason(savedBlockedDate.getReason());

                return response;
        }

        public List<VendorBlockedDateResponse> getBlockedDates(
                        Long vendorId) {

                List<VendorBlockedDate> blockedDates = vendorBlockedDateRepository.findByVendor_Id(vendorId);

                return blockedDates.stream()
                                .map(blockedDate -> {
                                        VendorBlockedDateResponse response = new VendorBlockedDateResponse();
                                        response.setId(blockedDate.getId());
                                        response.setEventDate(
                                                        blockedDate.getEventDate());
                                        response.setSlotType(
                                                        blockedDate.getSlotType());
                                        response.setReason(
                                                        blockedDate.getReason());

                                        return response;
                                })
                                .toList();
        }
}