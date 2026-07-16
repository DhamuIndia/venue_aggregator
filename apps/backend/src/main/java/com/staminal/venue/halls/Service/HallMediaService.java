package com.staminal.venue.halls.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.staminal.venue.halls.Dto.CreateHallMediaRequest;
import com.staminal.venue.halls.Dto.HallMediaResponse;
import com.staminal.venue.halls.Entity.HallMedia;
import com.staminal.venue.halls.Entity.Halls;
import com.staminal.venue.halls.Repository.HallMediaRepository;
import com.staminal.venue.halls.Repository.HallRepository;

@Service
public class HallMediaService {

    @Autowired
    private HallRepository hallRepository;

    @Autowired
    private HallMediaRepository hallMediaRepository;

    public HallMediaResponse create(Long hallId, CreateHallMediaRequest request, Authentication authentication) {
        Halls hall = findOwnedHall(hallId, authentication);
        if (!hasText(request.getUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media URL is required");
        }

        HallMedia media = new HallMedia();

        media.setHallId(hall);
        media.setMediaType(hasText(request.getMediaType()) ? request.getMediaType().trim().toUpperCase() : "IMAGE");
        media.setUrl(request.getUrl());
        media.setPublicId(request.getPublicId());
        media.setIsPrimary(
                request.getIsPrimary() != null
                        ? request.getIsPrimary()
                        : false);
        media.setSortOrder(request.getSortOrder());
        media.setCreatedAt(LocalDateTime.now());

        HallMedia savedMedia = hallMediaRepository.save(media);
        if (Boolean.TRUE.equals(savedMedia.getIsPrimary())) {
            promotePrimaryMedia(hall, savedMedia);
        }

        return map(savedMedia);
    }

    public List<HallMediaResponse> getByHall(Long hallId, Authentication authentication) {
        Halls hall = findOwnedHall(hallId, authentication);

        return hallMediaRepository.findByHallId_Id(hallId)
                .stream()
                .map(this::map)
                .toList();
    }

    private HallMediaResponse map(HallMedia media) {

        HallMediaResponse response = new HallMediaResponse();

        response.setId(media.getId());
        response.setHallId(media.getHallId().getId());
        response.setMediaType(media.getMediaType());
        response.setUrl(media.getUrl());
        response.setIsPrimary(media.getIsPrimary());
        response.setSortOrder(media.getSortOrder());

        return response;
    }

    public HallMediaResponse updateHallMedia(
            Long hallId,
            Long mediaId,
            CreateHallMediaRequest request,
            Authentication authentication) {
        Halls hall = findOwnedHall(hallId, authentication);
        HallMedia media = hallMediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));

        if (media.getHallId().getId() != hallId.longValue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Media does not belong to this hall");
        }
        boolean wasPrimary = Boolean.TRUE.equals(media.getIsPrimary());

        if (hasText(request.getMediaType())) {
            media.setMediaType(request.getMediaType().trim().toUpperCase());
        }
        if (hasText(request.getUrl())) {
            media.setUrl(request.getUrl());
        }
        if (hasText(request.getPublicId())) {
            media.setPublicId(request.getPublicId());
        }
        if (request.getSortOrder() != null) {
            media.setSortOrder(request.getSortOrder());
        }
        if (request.getIsPrimary() != null) {
            media.setIsPrimary(request.getIsPrimary());
        }

        HallMedia savedMedia = hallMediaRepository.save(media);
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            promotePrimaryMedia(hall, savedMedia);
        } else if (Boolean.FALSE.equals(request.getIsPrimary()) && wasPrimary) {
            ensurePrimaryMedia(hall);
        }

        return map(savedMedia);
    }

    public void deleteHallMedia(
            Long hallId,
            Long mediaId,
            Authentication authentication) {
        Halls hall = findOwnedHall(hallId, authentication);
        HallMedia media = hallMediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));

        if (media.getHallId().getId() != hallId.longValue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Media does not belong to this hall");
        }

        boolean wasPrimary = Boolean.TRUE.equals(media.getIsPrimary());
        hallMediaRepository.delete(media);
        if (wasPrimary) {
            ensurePrimaryMedia(hall);
        }
    }

    private Halls findOwnedHall(Long hallId, Authentication authentication) {
        Long userId = currentUserId(authentication);
        Halls hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"));
        if (hall.getOwnerUserId() == null || !hall.getOwnerUserId().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hall does not belong to this owner");
        }
        return hall;
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session is invalid", exception);
        }
    }

    private void promotePrimaryMedia(Halls hall, HallMedia primaryMedia) {
        hallMediaRepository.findByHallId_Id(hall.getId())
                .forEach(media -> {
                    boolean shouldBePrimary = media.getId().equals(primaryMedia.getId());
                    if (!Boolean.valueOf(shouldBePrimary).equals(media.getIsPrimary())) {
                        media.setIsPrimary(shouldBePrimary);
                        hallMediaRepository.save(media);
                    }
                });
        hall.setCoverImageUrl(primaryMedia.getUrl());
        hall.setUpdatedAt(LocalDateTime.now());
        hallRepository.save(hall);
    }

    private void ensurePrimaryMedia(Halls hall) {
        List<HallMedia> mediaItems = hallMediaRepository.findByHallId_Id(hall.getId())
                .stream()
                .sorted((first, second) -> {
                    int orderComparison = Integer.compare(
                            first.getSortOrder() == null ? 0 : first.getSortOrder(),
                            second.getSortOrder() == null ? 0 : second.getSortOrder());
                    return orderComparison != 0 ? orderComparison : first.getId().compareTo(second.getId());
                })
                .toList();
        if (mediaItems.isEmpty()) {
            hall.setCoverImageUrl(null);
            hall.setUpdatedAt(LocalDateTime.now());
            hallRepository.save(hall);
            return;
        }
        promotePrimaryMedia(hall, mediaItems.get(0));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
