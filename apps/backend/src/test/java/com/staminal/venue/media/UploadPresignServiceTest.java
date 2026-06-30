package com.staminal.venue.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class UploadPresignServiceTest {

    private UploadPresignService uploadPresignService;

    @BeforeEach
    void setUp() {
        UploadStorageProperties properties = new UploadStorageProperties(
                "http://localhost:9000",
                "us-east-1",
                "venue-media",
                "venue_minio",
                "venue_minio_password",
                true,
                "http://localhost:9000/venue-media",
                10 * 1024 * 1024);
        uploadPresignService = new UploadPresignService(
                properties,
                Clock.fixed(Instant.parse("2026-06-29T05:30:00Z"), ZoneOffset.UTC));
    }

    @Test
    void presignCreatesVendorPortfolioUploadUrl() {
        PresignUploadRequest request = new PresignUploadRequest(
                "Reception Buffet.webp",
                "image/webp",
                845120L,
                UploadPurpose.VENDOR_PORTFOLIO);

        PresignUploadResponse response = uploadPresignService.presign(request, authentication("ROLE_VENDOR"));

        assertThat(response.method()).isEqualTo("PUT");
        assertThat(response.headers()).containsEntry("Content-Type", "image/webp");
        assertThat(response.storageKey())
                .startsWith("vendors/301/portfolio/2026/06/29/")
                .endsWith("-reception-buffet.webp");
        assertThat(response.publicUrl()).isEqualTo("http://localhost:9000/venue-media/" + response.storageKey());
        assertThat(response.uploadUrl()).startsWith("http://localhost:9000/venue-media/" + response.storageKey());
        assertThat(response.uploadUrl()).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
        assertThat(response.uploadUrl()).contains("X-Amz-SignedHeaders=content-type%3Bhost");
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-06-29T05:45:00Z"));
    }

    @Test
    void presignRejectsPurposeWhenRoleDoesNotMatch() {
        PresignUploadRequest request = new PresignUploadRequest(
                "gallery.jpg",
                "image/jpeg",
                512000L,
                UploadPurpose.OWNER_HALL_MEDIA);

        assertThatThrownBy(() -> uploadPresignService.presign(request, authentication("ROLE_VENDOR")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void presignRejectsUnsupportedContentType() {
        PresignUploadRequest request = new PresignUploadRequest(
                "gallery.gif",
                "image/gif",
                512000L,
                UploadPurpose.VENDOR_PORTFOLIO);

        assertThatThrownBy(() -> uploadPresignService.presign(request, authentication("ROLE_VENDOR")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getStatusCode())
                        .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void presignRejectsOversizedUpload() {
        PresignUploadRequest request = new PresignUploadRequest(
                "gallery.jpg",
                "image/jpeg",
                11 * 1024 * 1024L,
                UploadPurpose.VENDOR_PORTFOLIO);

        assertThatThrownBy(() -> uploadPresignService.presign(request, authentication("ROLE_VENDOR")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> assertThat(exception.getStatusCode())
                        .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    private Authentication authentication(String role) {
        return new UsernamePasswordAuthenticationToken(
                "301",
                null,
                List.of(new SimpleGrantedAuthority(role)));
    }
}
