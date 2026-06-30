package com.staminal.venue.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UploadStorageProperties {

    private final String presignEndpoint;
    private final String region;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private final boolean pathStyleAccess;
    private final String publicBaseUrl;
    private final long maxUploadSizeBytes;

    public UploadStorageProperties(
            @Value("${app.storage.s3.presign-endpoint}") String presignEndpoint,
            @Value("${app.storage.s3.region}") String region,
            @Value("${app.storage.s3.bucket}") String bucket,
            @Value("${app.storage.s3.access-key}") String accessKey,
            @Value("${app.storage.s3.secret-key}") String secretKey,
            @Value("${app.storage.s3.path-style-access}") boolean pathStyleAccess,
            @Value("${app.storage.s3.public-base-url}") String publicBaseUrl,
            @Value("${app.storage.media.max-upload-size-bytes}") long maxUploadSizeBytes) {
        this.presignEndpoint = presignEndpoint;
        this.region = region;
        this.bucket = bucket;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.pathStyleAccess = pathStyleAccess;
        this.publicBaseUrl = publicBaseUrl;
        this.maxUploadSizeBytes = maxUploadSizeBytes;
    }

    public String getPresignEndpoint() {
        return presignEndpoint;
    }

    public String getRegion() {
        return region;
    }

    public String getBucket() {
        return bucket;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public long getMaxUploadSizeBytes() {
        return maxUploadSizeBytes;
    }
}
