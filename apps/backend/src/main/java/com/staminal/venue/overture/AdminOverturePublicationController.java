package com.staminal.venue.overture;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/admin/overture-onboarding/publications")
@RequiredArgsConstructor
public class AdminOverturePublicationController {
    private final OverturePublicationService service;
    @GetMapping public ResponseEntity<OvertureResponse.Page<OverturePublicationResponse.Summary>> list(
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication authentication) {
        return noStore(service.list(page,size,authentication));
    }
    @GetMapping("/{hallId}") public ResponseEntity<OverturePublicationResponse.Detail> detail(@PathVariable long hallId,Authentication authentication) {
        return noStore(service.detail(hallId,authentication));
    }
    @PostMapping("/{hallId}/publish") public ResponseEntity<OverturePublicationResponse.Detail> publish(@PathVariable long hallId,
            @RequestBody OverturePublicationRequest.Publish request,Authentication authentication) {
        return noStore(service.publish(hallId,request,authentication));
    }
    @PostMapping("/{hallId}/unpublish") public ResponseEntity<OverturePublicationResponse.Detail> unpublish(@PathVariable long hallId,
            @RequestBody OverturePublicationRequest.Unpublish request,Authentication authentication) {
        return noStore(service.unpublish(hallId,request,authentication));
    }
    private static <T>ResponseEntity<T> noStore(T value) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
