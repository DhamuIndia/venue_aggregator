package com.staminal.venue.halls.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    public HallMediaResponse create(Long hallId, CreateHallMediaRequest request) {

        Halls hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new RuntimeException("Hall not found"));

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

    public List<HallMediaResponse> getByHall(Long hallId) {

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
            CreateHallMediaRequest request) {
        HallMedia media = hallMediaRepository.findById(mediaId)
                .orElseThrow(() -> new RuntimeException("Media not found"));

        if (media.getHallId().getId() != hallId) {
            throw new RuntimeException("Media does not belong to this hall");
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
            Long mediaId) {
        HallMedia media = hallMediaRepository.findById(mediaId)
                .orElseThrow(() -> new RuntimeException("Media not found"));

        hallMediaRepository.delete(media);
    }
}
