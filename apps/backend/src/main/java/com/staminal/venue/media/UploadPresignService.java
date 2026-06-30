package com.staminal.venue.media;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;

import com.staminal.venue.enums.UserRole;

@Service
public class UploadPresignService {

    private static final String SERVICE = "s3";
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final Duration EXPIRY = Duration.ofMinutes(15);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter AMZ_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final UploadStorageProperties properties;
    private final Clock clock;

    @Autowired
    public UploadPresignService(UploadStorageProperties properties) {
        this(properties, Clock.systemUTC());
    }

    UploadPresignService(UploadStorageProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public PresignUploadResponse presign(PresignUploadRequest request, Authentication authentication) {
        Long userId = currentUserId(authentication);
        UploadPurpose purpose = request == null ? null : request.purpose();
        validatePurpose(authentication, purpose);

        String contentType = contentType(request);
        validateSize(request);

        Instant now = Instant.now(clock);
        String storageKey = storageKey(purpose, userId, request.fileName(), contentType, now);
        SignedUpload signedUpload = signPutUrl(storageKey, contentType, now);

        return new PresignUploadResponse(
                signedUpload.uploadUrl(),
                storageKey,
                publicUrl(storageKey),
                "PUT",
                Map.of("Content-Type", contentType),
                now.plus(EXPIRY));
    }

    private void validatePurpose(Authentication authentication, UploadPurpose purpose) {
        if (purpose == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload purpose is required");
        }

        if (purpose == UploadPurpose.VENDOR_PORTFOLIO && !hasRole(authentication, UserRole.VENDOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "VENDOR role is required for vendor uploads");
        }
        if (purpose == UploadPurpose.OWNER_HALL_MEDIA && !hasRole(authentication, UserRole.HALL_OWNER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "HALL_OWNER role is required for hall uploads");
        }
    }

    private String contentType(PresignUploadRequest request) {
        if (request == null || request.contentType() == null || request.contentType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contentType is required");
        }

        String contentType = request.contentType().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only JPEG, PNG, and WebP uploads are allowed");
        }
        return contentType;
    }

    private void validateSize(PresignUploadRequest request) {
        if (request == null || request.sizeBytes() == null || request.sizeBytes() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sizeBytes must be greater than zero");
        }
        if (request.sizeBytes() > properties.getMaxUploadSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Upload size exceeds the configured limit");
        }
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user", exception);
        }
    }

    private boolean hasRole(Authentication authentication, UserRole role) {
        String authority = "ROLE_" + role.name();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private String storageKey(
            UploadPurpose purpose,
            Long userId,
            String fileName,
            String contentType,
            Instant now) {

        String prefix = purpose == UploadPurpose.VENDOR_PORTFOLIO
                ? "vendors/%d/portfolio".formatted(userId)
                : "owner-halls/%d/media".formatted(userId);
        String datePath = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC).format(now);
        String baseName = sanitizedBaseName(fileName);
        String extension = extension(contentType);

        return "%s/%s/%s-%s.%s".formatted(
                prefix,
                datePath,
                UUID.randomUUID(),
                baseName,
                extension);
    }

    private String sanitizedBaseName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "upload";
        }

        String name = fileName.trim();
        int extensionIndex = name.lastIndexOf('.');
        if (extensionIndex > 0) {
            name = name.substring(0, extensionIndex);
        }

        String sanitized = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (sanitized.isBlank()) {
            return "upload";
        }
        return sanitized.length() > 80 ? sanitized.substring(0, 80).replaceAll("-$", "") : sanitized;
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type");
        };
    }

    private SignedUpload signPutUrl(String storageKey, String contentType, Instant now) {
        URI endpoint = endpoint();
        String host = host(endpoint);
        String path = objectPath(endpoint, storageKey);

        String amzDate = AMZ_DATE_FORMAT.format(now);
        String date = DATE_FORMAT.format(now);
        String credentialScope = "%s/%s/%s/aws4_request".formatted(date, properties.getRegion(), SERVICE);
        String signedHeaders = "content-type;host";

        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("X-Amz-Algorithm", ALGORITHM);
        queryParams.put("X-Amz-Credential", properties.getAccessKey() + "/" + credentialScope);
        queryParams.put("X-Amz-Date", amzDate);
        queryParams.put("X-Amz-Expires", String.valueOf(EXPIRY.toSeconds()));
        queryParams.put("X-Amz-SignedHeaders", signedHeaders);

        String canonicalQuery = canonicalQuery(queryParams);
        String canonicalHeaders = "content-type:%s\nhost:%s\n".formatted(contentType, host);
        String canonicalRequest = "PUT\n%s\n%s\n%s\n%s\nUNSIGNED-PAYLOAD".formatted(
                encodePath(path),
                canonicalQuery,
                canonicalHeaders,
                signedHeaders);
        String stringToSign = "%s\n%s\n%s\n%s".formatted(
                ALGORITHM,
                amzDate,
                credentialScope,
                sha256Hex(canonicalRequest));

        String signature = HexFormat.of().formatHex(hmac(signingKey(date), stringToSign));
        String uploadUrl = "%s://%s%s?%s&X-Amz-Signature=%s".formatted(
                endpoint.getScheme(),
                host,
                encodePath(path),
                canonicalQuery,
                signature);

        return new SignedUpload(uploadUrl);
    }

    private URI endpoint() {
        String configured = properties.getPresignEndpoint();
        if (configured == null || configured.isBlank()) {
            configured = "https://s3.%s.amazonaws.com".formatted(properties.getRegion());
        }
        URI endpoint = URI.create(configured.trim().replaceAll("/+$", ""));
        if (endpoint.getScheme() == null || endpoint.getHost() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "S3 presign endpoint is invalid");
        }
        return endpoint;
    }

    private String host(URI endpoint) {
        String baseHost = endpoint.getHost();
        int port = endpoint.getPort();
        String host = properties.isPathStyleAccess() ? baseHost : properties.getBucket() + "." + baseHost;
        return port == -1 ? host : host + ":" + port;
    }

    private String objectPath(URI endpoint, String storageKey) {
        String basePath = endpoint.getRawPath() == null ? "" : endpoint.getRawPath().replaceAll("/+$", "");
        if (properties.isPathStyleAccess()) {
            return basePath + "/" + properties.getBucket() + "/" + storageKey;
        }
        return basePath + "/" + storageKey;
    }

    private String publicUrl(String storageKey) {
        String baseUrl = properties.getPublicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            URI endpoint = endpoint();
            String path = objectPath(endpoint, storageKey);
            return "%s://%s%s".formatted(endpoint.getScheme(), host(endpoint), encodePath(path));
        }
        return baseUrl.replaceAll("/+$", "") + "/" + encodePath(storageKey);
    }

    private String canonicalQuery(Map<String, String> queryParams) {
        return queryParams.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> percentEncode(entry.getKey()) + "=" + percentEncode(entry.getValue()))
                .reduce((first, second) -> first + "&" + second)
                .orElse("");
    }

    private String encodePath(String path) {
        String[] parts = path.split("/", -1);
        StringBuilder encoded = new StringBuilder();
        for (int index = 0; index < parts.length; index++) {
            if (index > 0) {
                encoded.append('/');
            }
            encoded.append(percentEncode(parts[index]));
        }
        return encoded.toString();
    }

    private String percentEncode(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            int character = item & 0xff;
            if ((character >= 'A' && character <= 'Z')
                    || (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '-'
                    || character == '_'
                    || character == '.'
                    || character == '~') {
                encoded.append((char) character);
            } else {
                encoded.append('%');
                encoded.append(Character.toUpperCase(Character.forDigit((character >> 4) & 0xf, 16)));
                encoded.append(Character.toUpperCase(Character.forDigit(character & 0xf, 16)));
            }
        }
        return encoded.toString();
    }

    private byte[] signingKey(String date) {
        byte[] dateKey = hmac(("AWS4" + properties.getSecretKey()).getBytes(StandardCharsets.UTF_8), date);
        byte[] regionKey = hmac(dateKey, properties.getRegion());
        byte[] serviceKey = hmac(regionKey, SERVICE);
        return hmac(serviceKey, "aws4_request");
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate SHA-256 hash", exception);
        }
    }

    private byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate HMAC", exception);
        }
    }

    private record SignedUpload(String uploadUrl) {
    }
}
