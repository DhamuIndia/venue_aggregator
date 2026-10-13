package com.staminal.venue.overture;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import com.staminal.venue.overture.OvertureDraftMediaResponse.Gallery;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/overture-onboarding/drafts/{hallId}/media")
@RequiredArgsConstructor
public class AdminOvertureDraftMediaController {
    private final OvertureDraftMediaService service;
    @GetMapping public ResponseEntity<Gallery> gallery(@PathVariable long hallId, Authentication authentication) { return noStore(service.gallery(hallId, authentication)); }
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Gallery> upload(@PathVariable long hallId, @RequestPart("file") MultipartFile file,
            @RequestPart("metadata") OvertureDraftMediaRequest.Upload metadata, Authentication authentication) {
        return noStore(service.upload(hallId, file, metadata, authentication));
    }
    @PutMapping("/{mediaId}/review") public ResponseEntity<Gallery> review(@PathVariable long hallId, @PathVariable long mediaId,
            @RequestBody OvertureDraftMediaRequest.Review request, Authentication authentication) { return noStore(service.review(hallId, mediaId, request, authentication)); }
    @PutMapping("/arrangement") public ResponseEntity<Gallery> arrange(@PathVariable long hallId,
            @RequestBody OvertureDraftMediaRequest.Arrangement request, Authentication authentication) { return noStore(service.arrange(hallId, request, authentication)); }
    @PostMapping("/{mediaId}/archive") public ResponseEntity<Gallery> archive(@PathVariable long hallId, @PathVariable long mediaId,
            @RequestBody OvertureDraftMediaRequest.Archive request, Authentication authentication) { return noStore(service.archive(hallId, mediaId, request, authentication)); }
    @GetMapping("/{mediaId}/content") public ResponseEntity<byte[]> content(@PathVariable long hallId, @PathVariable long mediaId, Authentication authentication) {
        byte[] bytes = service.content(hallId, mediaId, authentication);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).contentLength(bytes.length)
                .header("Cache-Control", "private, no-store").header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header("Content-Disposition", "inline; filename=\"draft-photo-" + mediaId + ".jpg\"").body(bytes);
    }
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<org.springframework.http.ProblemDetail> invalidMultipart(MultipartException exception) {
        // Local handlers precede global advice; preserve the same 413 contract when a handler is already selected.
        if (exception instanceof MaxUploadSizeExceededException)
            return ResponseEntity.status(413).cacheControl(CacheControl.noStore()).body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                    org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "Photo upload exceeds the permitted size"));
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(org.springframework.http.ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Photo upload multipart body is invalid"));
    }
    private ResponseEntity<Gallery> noStore(Gallery gallery) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(gallery); }
}
