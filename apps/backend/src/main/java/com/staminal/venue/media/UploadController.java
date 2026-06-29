package com.staminal.venue.media;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadPresignService uploadPresignService;

    @PostMapping("/presign")
    public PresignUploadResponse presign(
            @RequestBody PresignUploadRequest request,
            Authentication authentication) {
        return uploadPresignService.presign(request, authentication);
    }
}
