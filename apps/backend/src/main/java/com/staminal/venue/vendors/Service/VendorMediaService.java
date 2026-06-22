package com.staminal.venue.vendors.Service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.staminal.venue.vendors.Dto.CreateVendorMediaRequest;
import com.staminal.venue.vendors.Dto.VendorMediaResponse;
import com.staminal.venue.vendors.Entity.VendorMedia;
import com.staminal.venue.vendors.Entity.Vendors;
import com.staminal.venue.vendors.Repository.VendorMediaRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorMediaService {

    private final VendorMediaRepository vendorMediaRepository;
    private final VendorRepository vendorRepository;

    public VendorMediaResponse createMedia(CreateVendorMediaRequest request) {

        Vendors vendor = vendorRepository.findById(request.getVendorId())
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        VendorMedia media = new VendorMedia();
        media.setVendor(vendor);
        media.setMediaUrl(request.getMediaUrl());
        media.setIsPrimary(request.isPrimary());
        media.setCreatedAt(Instant.now());
        media.setServiceType(request.getServiceType());
        media.setServiceId(request.getServiceId());

        VendorMedia vendorMedia = vendorMediaRepository.save(media);

        VendorMediaResponse response = new VendorMediaResponse();
        response.setId(vendorMedia.getId());
        response.setMediaUrl(vendorMedia.getMediaUrl());
        response.setPrimary(vendorMedia.getIsPrimary());
        response.setServiceType(vendorMedia.getServiceType());
        response.setServiceId(vendorMedia.getServiceId());
        return response;

    }

    public List<VendorMediaResponse> getVendorMedia(Long vendorId) {

        List<VendorMedia> mediaList = vendorMediaRepository.findByVendor_Id(vendorId);

        return mediaList.stream()
                .map(media -> {
                    VendorMediaResponse response = new VendorMediaResponse();
                    response.setId(media.getId());
                    response.setMediaUrl(media.getMediaUrl());
                    response.setPrimary(media.getIsPrimary());
                    response.setServiceType(media.getServiceType());
                    response.setServiceId(media.getServiceId());
                    return response;
                })
                .toList();
    }
}