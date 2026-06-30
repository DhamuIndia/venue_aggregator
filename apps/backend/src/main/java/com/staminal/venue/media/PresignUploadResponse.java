package com.staminal.venue.media;

import java.time.Instant;
import java.util.Map;

public record PresignUploadResponse(
        String uploadUrl,
        String storageKey,
        String publicUrl,
        String method,
        Map<String, String> headers,
        Instant expiresAt) {
}
