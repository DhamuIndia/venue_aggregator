package com.staminal.venue.overture;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.staminal.venue.overture.OvertureDraftMediaResponse.Gallery;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/overture-onboarding/drafts/{hallId}/media/{mediaId}/credits")
@RequiredArgsConstructor
public class AdminOverturePhotoCreditController {
    private final OverturePhotoCreditService service;
    @PutMapping public ResponseEntity<Gallery> save(@PathVariable long hallId, @PathVariable long mediaId,
            @RequestBody OverturePhotoCreditRequest.Save request, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.save(hallId, mediaId, request, authentication));
    }
    @PostMapping("/review") public ResponseEntity<Gallery> review(@PathVariable long hallId, @PathVariable long mediaId,
            @RequestBody OverturePhotoCreditRequest.Review request, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.review(hallId, mediaId, request, authentication));
    }
}
