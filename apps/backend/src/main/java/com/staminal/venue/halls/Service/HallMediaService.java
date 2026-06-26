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

        HallMedia media = new HallMedia();

        media.setHallId(hall);
        media.setMediaType(request.getMediaType());
        media.setUrl(request.getUrl());
        media.setPublicId(request.getPublicId());
        media.setIsPrimary(request.getIsPrimary());
        media.setSortOrder(request.getSortOrder());
        media.setCreatedAt(LocalDateTime.now());

        hallMediaRepository.save(media);

        return map(media);
    }

    public List<HallMediaResponse> getByHall(Long hallId, Authentication authentication) {
        findOwnedHall(hallId, authentication);

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
        findOwnedHall(hallId, authentication);
        HallMedia media = hallMediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));

        if (media.getHallId().getId() != hallId.longValue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Media does not belong to this hall");
        }

        media.setMediaType(request.getMediaType());
        media.setUrl(request.getUrl());
        media.setPublicId(request.getPublicId());
        media.setIsPrimary(request.getIsPrimary());
        media.setSortOrder(request.getSortOrder());

        hallMediaRepository.save(media);

        return map(media);
    }

    public void deleteHallMedia(
            Long hallId,
            Long mediaId,
            Authentication authentication) {
        findOwnedHall(hallId, authentication);
        HallMedia media = hallMediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));

        if (media.getHallId().getId() != hallId.longValue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Media does not belong to this hall");
        }

        hallMediaRepository.delete(media);
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
}
