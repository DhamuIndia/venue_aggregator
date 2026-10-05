package com.staminal.venue.overture;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.staminal.venue.overture.OvertureResponse.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/overture-onboarding")
@RequiredArgsConstructor
public class AdminOvertureOnboardingController {
    private final AdminOvertureOnboardingService service;
    private final OvertureDraftReviewService reviews;

    @GetMapping("/settings")
    public ResponseEntity<Settings> settings(Authentication authentication) { return noStore(service.settings(authentication)); }

    @GetMapping("/catalog")
    public ResponseEntity<CatalogPage> catalog(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String query,
            Authentication authentication) { return noStore(service.catalog(page, size, query, authentication)); }

    @PostMapping("/preview")
    public ResponseEntity<Preview> preview(@Valid @RequestBody OvertureRequest.Preview request,
            Authentication authentication) { return noStore(service.preview(request, authentication)); }

    @PostMapping("/import")
    public ResponseEntity<ImportResult> importDrafts(@Valid @RequestBody OvertureRequest.Import request,
            Authentication authentication) { return noStore(service.importDrafts(request, authentication)); }

    @GetMapping("/drafts")
    public ResponseEntity<Page<Draft>> drafts(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Authentication authentication) { return noStore(service.drafts(page, size, authentication)); }

    @GetMapping("/drafts/{hallId}")
    public ResponseEntity<OvertureDraftReviewResponse.Detail> detail(@PathVariable long hallId, Authentication authentication) {
        return noStore(reviews.detail(hallId, authentication));
    }

    @PutMapping("/drafts/{hallId}")
    public ResponseEntity<OvertureDraftReviewResponse.Detail> update(@PathVariable long hallId,
            @RequestBody OvertureDraftReviewRequest.Update request, Authentication authentication) {
        return noStore(reviews.update(hallId, request, authentication));
    }

    private <T> ResponseEntity<T> noStore(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
