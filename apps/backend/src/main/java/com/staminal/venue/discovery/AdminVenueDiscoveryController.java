package com.staminal.venue.discovery;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.staminal.venue.discovery.VenueDiscoveryResponse.*;
import com.staminal.venue.discovery.places.PlacePreview;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/venue-discovery")
@RequiredArgsConstructor
public class AdminVenueDiscoveryController {
    private final AdminVenueDiscoveryService service;

    @GetMapping("/settings")
    public ResponseEntity<Settings> settings(Authentication authentication) {
        return noStore(service.settings(authentication));
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResult> search(@Valid @RequestBody VenueDiscoveryRequest.Search request,
            Authentication authentication) {
        return noStore(service.search(request, authentication));
    }

    @GetMapping("/runs")
    public ResponseEntity<Page<Run>> runs(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Authentication authentication) {
        return noStore(service.runs(page, size, authentication));
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<RunDetail> run(@PathVariable Long id, Authentication authentication) {
        return noStore(service.run(id, authentication));
    }

    @GetMapping("/candidates")
    public ResponseEntity<Page<Candidate>> candidates(@RequestParam(required = false) VenueDiscoveryCandidateStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return noStore(service.candidates(status, page, size, authentication));
    }

    @PostMapping("/candidates/{id}/preview")
    public ResponseEntity<PlacePreview> preview(@PathVariable Long id, Authentication authentication) {
        return noStore(service.preview(id, authentication));
    }

    @PatchMapping("/candidates/{id}")
    public ResponseEntity<Candidate> review(@PathVariable Long id, @Valid @RequestBody VenueDiscoveryRequest.Review request,
            Authentication authentication) {
        return noStore(service.review(id, request, authentication));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
