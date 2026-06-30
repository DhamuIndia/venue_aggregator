package com.staminal.venue.vendors.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.enums.VendorServiceType;
import com.staminal.venue.vendors.Dto.CreateVendorMediaRequest;
import com.staminal.venue.vendors.Dto.VendorMediaResponse;
import com.staminal.venue.vendors.Entity.VendorCategory;
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

    @Transactional(readOnly = true)
    public List<VendorMediaResponse> getMyMedia(Authentication authentication) {
        Long userId = currentUserId(authentication);

        return vendorMediaRepository.findByVendor_User_Id(userId)
                .stream()
                .sorted(mediaSort())
                .map(this::map)
                .toList();
    }

    @Transactional
    public VendorMediaResponse createMyMedia(CreateVendorMediaRequest request, Authentication authentication) {
        Vendors vendor = currentVendor(currentUserId(authentication));

        VendorMedia media = new VendorMedia();
        media.setVendor(vendor);
        media.setCreatedAt(Instant.now());
        applyCreateRequest(media, vendor, request);

        if (media.getIsPrimary()) {
            clearPrimary(vendor.getId(), null);
            vendor.setCoverImageUrl(media.getMediaUrl());
        }

        VendorMedia saved = vendorMediaRepository.save(media);
        vendor.setUpdatedAt(Instant.now());
        vendorRepository.save(vendor);

        return map(saved);
    }

    @Transactional
    public VendorMediaResponse updateMyMedia(
            String mediaId,
            CreateVendorMediaRequest request,
            Authentication authentication) {

        Long userId = currentUserId(authentication);
        VendorMedia media = findOwnedMedia(parseMediaId(mediaId), userId);
        Vendors vendor = media.getVendor();

        applyPatchRequest(media, request);

        if (media.getIsPrimary()) {
            clearPrimary(vendor.getId(), media.getId());
            vendor.setCoverImageUrl(media.getMediaUrl());
        }

        VendorMedia saved = vendorMediaRepository.save(media);
        vendor.setUpdatedAt(Instant.now());
        vendorRepository.save(vendor);

        return map(saved);
    }

    @Transactional
    public void deleteMyMedia(String mediaId, Authentication authentication) {
        Long userId = currentUserId(authentication);
        VendorMedia media = findOwnedMedia(parseMediaId(mediaId), userId);
        Vendors vendor = media.getVendor();
        boolean deletedCover = media.getIsPrimary();

        vendorMediaRepository.delete(media);

        if (deletedCover) {
            promoteNextCover(vendor);
        }

        vendor.setUpdatedAt(Instant.now());
        vendorRepository.save(vendor);
    }

    @Transactional
    public VendorMediaResponse createMedia(CreateVendorMediaRequest request) {

        Vendors vendor = vendorRepository.findById(request.getVendorId())
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        VendorMedia media = new VendorMedia();
        media.setVendor(vendor);
        media.setMediaUrl(firstText(request.getMediaUrl(), request.getUrl()));
        media.setIsPrimary(request.isPrimary());
        media.setCreatedAt(Instant.now());
        media.setServiceType(request.getServiceType());
        media.setServiceId(request.getServiceId());
        media.setMediaType(firstText(request.getMediaType(), request.getType(), "IMAGE"));
        media.setStorageKey(trimToNull(request.getStorageKey()));
        media.setFileName(trimToNull(request.getFileName()));
        media.setCaption(trimToNull(request.getCaption()));
        media.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());

        VendorMedia vendorMedia = vendorMediaRepository.save(media);

        return map(vendorMedia);

    }

    @Transactional(readOnly = true)
    public List<VendorMediaResponse> getVendorMedia(Long vendorId) {

        List<VendorMedia> mediaList = vendorMediaRepository.findByVendor_Id(vendorId);

        return mediaList.stream()
                .sorted(mediaSort())
                .map(this::map)
                .toList();
    }

    private void applyCreateRequest(VendorMedia media, Vendors vendor, CreateVendorMediaRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media details are required");
        }

        String url = firstText(request.getUrl(), request.getMediaUrl());
        if (url == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media URL is required");
        }

        List<VendorMedia> existingMedia = vendorMediaRepository.findByVendor_Id(vendor.getId());
        boolean shouldBeCover = Boolean.TRUE.equals(request.getIsCover())
                || request.isPrimary()
                || existingMedia.isEmpty();

        media.setMediaUrl(url);
        media.setMediaType(mediaType(request));
        media.setStorageKey(trimToNull(request.getStorageKey()));
        media.setFileName(trimToNull(request.getFileName()));
        media.setCaption(caption(request));
        media.setIsPrimary(shouldBeCover);
        media.setSortOrder(request.getSortOrder() == null ? existingMedia.size() : Math.max(request.getSortOrder(), 0));
        media.setServiceType(serviceTypeFor(vendor));
        media.setServiceId(vendor.getId());
    }

    private void applyPatchRequest(VendorMedia media, CreateVendorMediaRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media details are required");
        }

        String url = firstText(request.getUrl(), request.getMediaUrl());
        if (url != null) {
            media.setMediaUrl(url);
        }
        if (request.getStorageKey() != null) {
            media.setStorageKey(trimToNull(request.getStorageKey()));
        }
        if (request.getFileName() != null) {
            media.setFileName(trimToNull(request.getFileName()));
        }
        if (request.getCaption() != null) {
            media.setCaption(caption(request));
        }
        if (request.getSortOrder() != null) {
            media.setSortOrder(Math.max(request.getSortOrder(), 0));
        }
        if (request.getMediaType() != null || request.getType() != null) {
            media.setMediaType(mediaType(request));
        }
        if (request.getIsCover() != null || request.getPrimary() != null) {
            media.setIsPrimary(Boolean.TRUE.equals(request.getIsCover()) || request.isPrimary());
        }
    }

    private VendorMedia findOwnedMedia(Long mediaId, Long userId) {
        return vendorMediaRepository.findByIdAndVendor_User_Id(mediaId, userId)
                .orElseThrow(() -> {
                    if (vendorMediaRepository.findById(mediaId).isPresent()) {
                        return new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Media does not belong to the logged-in vendor");
                    }
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found");
                });
    }

    private void clearPrimary(Long vendorId, Long exceptMediaId) {
        vendorMediaRepository.findByVendor_Id(vendorId)
                .stream()
                .filter(media -> exceptMediaId == null || !exceptMediaId.equals(media.getId()))
                .filter(VendorMedia::getIsPrimary)
                .forEach(media -> {
                    media.setIsPrimary(false);
                    vendorMediaRepository.save(media);
                });
    }

    private void promoteNextCover(Vendors vendor) {
        List<VendorMedia> remaining = vendorMediaRepository.findByVendor_Id(vendor.getId())
                .stream()
                .sorted(mediaSort())
                .toList();

        if (remaining.isEmpty()) {
            vendor.setCoverImageUrl("");
            return;
        }

        VendorMedia nextCover = remaining.get(0);
        nextCover.setIsPrimary(true);
        vendorMediaRepository.save(nextCover);
        vendor.setCoverImageUrl(nextCover.getMediaUrl());
    }

    private Vendors currentVendor(Long userId) {
        return vendorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Create vendor profile before managing media"));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
        }
    }

    private Long parseMediaId(String mediaId) {
        try {
            return Long.parseLong(mediaId);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found");
        }
    }

    private VendorMediaResponse map(VendorMedia media) {
        VendorMediaResponse response = new VendorMediaResponse();
        response.setId(media.getId());
        response.setUrl(media.getMediaUrl());
        response.setMediaUrl(media.getMediaUrl());
        response.setPrimary(media.getIsPrimary());
        response.setIsCover(media.getIsPrimary());
        response.setStorageKey(media.getStorageKey());
        response.setFileName(media.getFileName());
        response.setCaption(media.getCaption());
        response.setSortOrder(media.getSortOrder() == null ? 0 : media.getSortOrder());
        response.setMediaType(firstText(media.getMediaType(), "IMAGE"));
        response.setServiceType(media.getServiceType());
        response.setServiceId(media.getServiceId());
        return response;
    }

    private Comparator<VendorMedia> mediaSort() {
        return Comparator
                .comparing((VendorMedia media) -> media.getSortOrder() == null ? 0 : media.getSortOrder())
                .thenComparing(VendorMedia::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private String mediaType(CreateVendorMediaRequest request) {
        String mediaType = firstText(request.getMediaType(), request.getType(), "IMAGE").toUpperCase(Locale.ROOT);
        if (!Set.of("IMAGE", "VIDEO").contains(mediaType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media type must be IMAGE or VIDEO");
        }
        return mediaType;
    }

    private String caption(CreateVendorMediaRequest request) {
        String caption = firstText(request.getCaption(), request.getFileName());
        if (caption != null && caption.length() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Caption must be 180 characters or less");
        }
        return caption;
    }

    private VendorServiceType serviceTypeFor(Vendors vendor) {
        String categoryText = vendor.getCategories() == null
                ? ""
                : vendor.getCategories().stream()
                        .map(VendorCategory::getCategoryName)
                        .filter(value -> value != null && !value.isBlank())
                        .findFirst()
                        .orElse("");
        String serviceText = vendor.getServices() == null ? "" : String.join(" ", vendor.getServices());
        String normalized = (categoryText + " " + serviceText).toUpperCase(Locale.ROOT);

        if (normalized.contains("PHOTO")) {
            return VendorServiceType.PHOTOGRAPHY;
        }
        if (normalized.contains("MAKEUP") || normalized.contains("MEHENDI")) {
            return VendorServiceType.MAKEUP;
        }
        if (normalized.contains("DJ") || normalized.contains("MUSIC")) {
            return VendorServiceType.DJ;
        }
        if (normalized.contains("DECOR")) {
            return VendorServiceType.DECORATION;
        }
        return VendorServiceType.CATERING;
    }

    private String firstText(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
