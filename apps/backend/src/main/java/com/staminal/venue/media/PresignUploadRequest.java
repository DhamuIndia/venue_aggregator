package com.staminal.venue.media;

public record PresignUploadRequest(
        String fileName,
        String contentType,
        Long sizeBytes,
        UploadPurpose purpose) {
}
