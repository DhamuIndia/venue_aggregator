package com.staminal.venue.overture;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/halls/{hallId}/application-photos")
@RequiredArgsConstructor
public class PublicApplicationPhotoController {
    private final OverturePublicationService service;
    @GetMapping("/{photoId}") public ResponseEntity<byte[]> photo(@PathVariable long hallId,@PathVariable long photoId,@RequestParam long publicationVersion) {
        byte[] bytes=service.publicPhoto(hallId,photoId,publicationVersion);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).contentLength(bytes.length)
                .header("Cache-Control","private, no-store")
                .header("X-Content-Type-Options","nosniff")
                .header("Content-Security-Policy","default-src 'none'; sandbox")
                .header("Content-Disposition","inline; filename=\"venue-photo-"+photoId+".jpg\"")
                .body(bytes);
    }
}
