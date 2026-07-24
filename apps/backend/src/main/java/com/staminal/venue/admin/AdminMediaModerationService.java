package com.staminal.venue.admin;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.halls.Entity.HallMedia;
import com.staminal.venue.halls.Repository.HallMediaRepository;
import com.staminal.venue.halls.Repository.HallRepository;
import com.staminal.venue.vendors.Entity.VendorMedia;
import com.staminal.venue.vendors.Repository.VendorMediaRepository;
import com.staminal.venue.vendors.Repository.VendorRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminMediaModerationService {

    private final HallMediaRepository hallMediaRepository;
    private final HallRepository hallRepository;
    private final VendorMediaRepository vendorMediaRepository;
    private final VendorRepository vendorRepository;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> pendingMedia() {
        List<Map<String, Object>> halls = hallMediaRepository.findByApprovedFalse().stream()
                .map(media -> Map.<String, Object>of(
                        "id", media.getId(), "type", "HALL", "listingId", media.getHallId().getId(),
                        "listingName", media.getHallId().getName(), "url", media.getUrl(),
                        "isPrimary", Boolean.TRUE.equals(media.getIsPrimary())))
                .toList();
        List<Map<String, Object>> vendors = vendorMediaRepository.findByApprovedFalse().stream()
                .map(media -> Map.<String, Object>of(
                        "id", media.getId(), "type", "VENDOR", "listingId", media.getVendor().getId(),
                        "listingName", media.getVendor().getBusinessName(), "url", media.getMediaUrl(),
                        "isPrimary", media.getIsPrimary()))
                .toList();
        return java.util.stream.Stream.concat(halls.stream(), vendors.stream()).toList();
    }

    @Transactional
    public void approve(String type, Long mediaId) {
        if ("HALL".equalsIgnoreCase(type)) {
            HallMedia media = hallMediaRepository.findById(mediaId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall media not found"));
            media.setApproved(true);
            hallMediaRepository.save(media);
            if (Boolean.TRUE.equals(media.getIsPrimary())) {
                hallMediaRepository.findByHallId_Id(media.getHallId().getId()).stream()
                        .filter(other -> !other.getId().equals(media.getId()))
                        .filter(other -> Boolean.TRUE.equals(other.getIsPrimary()))
                        .forEach(other -> {
                            other.setIsPrimary(false);
                            hallMediaRepository.save(other);
                        });
                media.getHallId().setCoverImageUrl(media.getUrl());
                media.getHallId().setUpdatedAt(LocalDateTime.now());
                hallRepository.save(media.getHallId());
            }
            return;
        }
        if ("VENDOR".equalsIgnoreCase(type)) {
            VendorMedia media = vendorMediaRepository.findById(mediaId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor media not found"));
            media.setApproved(true);
            vendorMediaRepository.save(media);
            if (media.getIsPrimary()) {
                vendorMediaRepository.findByVendor_Id(media.getVendor().getId()).stream()
                        .filter(other -> !other.getId().equals(media.getId()))
                        .filter(VendorMedia::getIsPrimary)
                        .forEach(other -> {
                            other.setIsPrimary(false);
                            vendorMediaRepository.save(other);
                        });
                media.getVendor().setCoverImageUrl(media.getMediaUrl());
                media.getVendor().setUpdatedAt(Instant.now());
                vendorRepository.save(media.getVendor());
            }
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media type must be HALL or VENDOR");
    }

    @Transactional
    public void reject(String type, Long mediaId) {

        if ("HALL".equalsIgnoreCase(type)) {
            HallMedia media = hallMediaRepository.findById(mediaId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Hall media not found"));

            hallMediaRepository.delete(media);
            return;
        }

        if ("VENDOR".equalsIgnoreCase(type)) {
            VendorMedia media = vendorMediaRepository.findById(mediaId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Vendor media not found"));

            vendorMediaRepository.delete(media);
            return;
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Media type must be HALL or VENDOR");
    }
}
