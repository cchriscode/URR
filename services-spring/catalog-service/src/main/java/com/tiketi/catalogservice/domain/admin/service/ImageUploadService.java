package com.tiketi.catalogservice.domain.admin.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class ImageUploadService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final String awsRegion;
    private final String bucket;

    public ImageUploadService(
        @Value("${aws.region:}") String awsRegion,
        @Value("${aws.s3.bucket:}") String bucket
    ) {
        this.awsRegion = awsRegion;
        this.bucket = bucket;
    }

    public Map<String, String> upload(MultipartFile image) {
        if (bucket == null || bucket.isBlank() || awsRegion == null || awsRegion.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "S3 configuration is missing");
        }
        if (image == null || image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        }
        if (image.getSize() > 5L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image size must be under 5MB");
        }

        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files (JPEG, PNG, WebP, GIF) are allowed");
        }

        String originalName = image.getOriginalFilename() != null ? image.getOriginalFilename() : "image";
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0) {
            ext = originalName.substring(dot);
            originalName = originalName.substring(0, dot);
        }

        String sanitized = sanitize(originalName);
        String key = "files/" + Instant.now().toEpochMilli() + "_" + sanitized + ext;

        try (S3Client s3Client = S3Client.builder().region(Region.of(awsRegion)).build()) {
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

            s3Client.putObject(request, RequestBody.fromBytes(image.getBytes()));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image upload failed");
        }

        String url = "https://" + bucket + ".s3." + awsRegion + ".amazonaws.com/" + key;
        return Map.of("url", url, "key", key);
    }

    private String sanitize(String value) {
        String sanitized = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.isBlank()) {
            sanitized = new String("file".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        }
        return sanitized;
    }
}
