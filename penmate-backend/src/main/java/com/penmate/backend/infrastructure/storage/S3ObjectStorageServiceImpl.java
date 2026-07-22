package com.penmate.backend.infrastructure.storage;

import com.penmate.backend.domain.shared.service.ObjectStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseBytes;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class S3ObjectStorageServiceImpl implements ObjectStorageService {

    private final String storageEndpoint;
    private final String storageAccessKey;
    private final String storageSecretKey;
    private final String storageBucket;
    private final String storageRegion;
    private final Long storagePresignExpireMinutes;
    private final Boolean storageSecure;

    public S3ObjectStorageServiceImpl(@Value("${penmate.storage.endpoint:http://localhost:9000}") String storageEndpoint,
                                      @Value("${penmate.storage.access-key:minioadmin}") String storageAccessKey,
                                      @Value("${penmate.storage.secret-key:minioadmin}") String storageSecretKey,
                                      @Value("${penmate.storage.bucket:penmate}") String storageBucket,
                                      @Value("${penmate.storage.region:us-east-1}") String storageRegion,
                                      @Value("${penmate.storage.presign-expire-minutes:15}") Long storagePresignExpireMinutes,
                                      @Value("${penmate.storage.secure:true}") Boolean storageSecure) {
        this.storageEndpoint = storageEndpoint;
        this.storageAccessKey = storageAccessKey;
        this.storageSecretKey = storageSecretKey;
        this.storageBucket = storageBucket;
        this.storageRegion = storageRegion;
        this.storagePresignExpireMinutes = storagePresignExpireMinutes;
        this.storageSecure = storageSecure;
    }

    @Override
    public String buildReadUrl(String objectKey) {
        try (S3Presigner presigner = buildS3Presigner()) {
            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .bucket(storageBucket)
                    .key(objectKey)
                    .build();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(resolvePresignExpireMinutes()))
                    .getObjectRequest(objectRequest)
                    .build();
            return presigner.presignGetObject(presignRequest).url().toString();
        }
    }

    @Override
    public String buildUploadUrl(String objectKey, String contentType) {
        try (S3Presigner presigner = buildS3Presigner()) {
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(storageBucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(resolvePresignExpireMinutes()))
                    .putObjectRequest(objectRequest)
                    .build();
            return presigner.presignPutObject(presignRequest).url().toString();
        }
    }

    @Override
    public PutObjectResult putText(String objectKey, String content, String contentType) {
        byte[] bytes = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
        return putBytes(objectKey, bytes, contentType);
    }

    @Override
    public PutObjectResult putBytes(String objectKey, byte[] content, String contentType) {
        String endpoint = normalizedStorageEndpoint();
        URI endpointUri = URI.create(endpoint);
        boolean pathStyle = shouldUsePathStyle(endpointUri);
        byte[] bytes = content == null ? new byte[0] : content.clone();
        try (S3Client client = S3Client.builder()
                .endpointOverride(endpointUri)
                .region(Region.of(storageRegion))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(storageAccessKey, storageSecretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .chunkedEncodingEnabled(false)
                        .checksumValidationEnabled(false)
                        .build())
                .build()) {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(storageBucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();
            PutObjectResponse response = client.putObject(request, RequestBody.fromBytes(bytes));
            return new PutObjectResult(response.eTag(), (long) bytes.length, null);
        }
    }

    @Override
    public String readText(String objectKey) {
        return new String(readBytes(objectKey), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] readBytes(String objectKey) {
        String endpoint = normalizedStorageEndpoint();
        URI endpointUri = URI.create(endpoint);
        boolean pathStyle = shouldUsePathStyle(endpointUri);
        try (S3Client client = S3Client.builder()
                .endpointOverride(endpointUri)
                .region(Region.of(storageRegion))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(storageAccessKey, storageSecretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .chunkedEncodingEnabled(false)
                        .checksumValidationEnabled(false)
                        .build())
                .build()) {
            return client.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(storageBucket)
                            .key(objectKey)
                            .build())
                    .asByteArray();
        }
    }

    @Override
    public ObjectMetadata head(String objectKey) {
        String endpoint = normalizedStorageEndpoint();
        URI endpointUri = URI.create(endpoint);
        try (S3Client client = S3Client.builder()
                .endpointOverride(endpointUri)
                .region(Region.of(storageRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(storageAccessKey, storageSecretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(shouldUsePathStyle(endpointUri))
                        .chunkedEncodingEnabled(false)
                        .checksumValidationEnabled(false)
                        .build())
                .build()) {
            try {
                HeadObjectResponse response = client.headObject(HeadObjectRequest.builder()
                        .bucket(storageBucket)
                        .key(objectKey)
                        .build());
                String checksum = response.checksumSHA256() == null ? response.checksumCRC32C() : response.checksumSHA256();
                return new ObjectMetadata(response.eTag(), response.contentLength(), checksum, response.contentType());
            } catch (S3Exception exception) {
                if (exception.statusCode() != 403) throw exception;
            }
            // Some S3-compatible providers reject HEAD even when GetObject is allowed. They may also
            // reject unsigned Range headers, so fall back to a normal GET and retain its metadata.
            ResponseBytes<GetObjectResponse> storedObject = client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(storageBucket)
                    .key(objectKey)
                    .build());
            GetObjectResponse response = storedObject.response();
            String checksum = response.checksumSHA256() == null ? response.checksumCRC32C() : response.checksumSHA256();
            return new ObjectMetadata(response.eTag(), resolveObjectSize(response), checksum, response.contentType());
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try (S3Client client = S3Client.builder()
                .endpointOverride(URI.create(normalizedStorageEndpoint()))
                .region(Region.of(storageRegion))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(storageAccessKey, storageSecretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(shouldUsePathStyle(URI.create(normalizedStorageEndpoint())))
                        .chunkedEncodingEnabled(false)
                        .checksumValidationEnabled(false)
                        .build())
                .build()) {
            try {
                client.headObject(HeadObjectRequest.builder().bucket(storageBucket).key(objectKey).build());
                return true;
            } catch (S3Exception exception) {
                if (exception.statusCode() == 404) return false;
                if (exception.statusCode() != 403) throw exception;
                client.getObjectAsBytes(GetObjectRequest.builder().bucket(storageBucket).key(objectKey).build());
                return true;
            }
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) return false;
            throw ex;
        }
    }

    @Override
    public void delete(String objectKey) {
        String endpoint = normalizedStorageEndpoint();
        URI endpointUri = URI.create(endpoint);
        try (S3Client client = S3Client.builder()
                .endpointOverride(endpointUri)
                .region(Region.of(storageRegion))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(storageAccessKey, storageSecretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(shouldUsePathStyle(endpointUri))
                        .chunkedEncodingEnabled(false)
                        .checksumValidationEnabled(false)
                        .build())
                .build()) {
            client.deleteObject(DeleteObjectRequest.builder().bucket(storageBucket).key(objectKey).build());
        }
    }

    private S3Presigner buildS3Presigner() {
        String endpoint = normalizedStorageEndpoint();
        URI endpointUri = URI.create(endpoint);
        boolean pathStyle = shouldUsePathStyle(endpointUri);
        return S3Presigner.builder()
                .endpointOverride(endpointUri)
                .region(Region.of(storageRegion))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(storageAccessKey, storageSecretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build())
                .build();
    }

    private boolean shouldUsePathStyle(URI endpointUri) {
        String host = endpointUri.getHost();
        if (host == null || host.isBlank()) {
            return true;
        }
        String normalizedHost = host.toLowerCase();
        // 非 AWS 官方域名默认走 path-style，避免生成 bucket 前缀子域名（如 penmate.s3.xxxhub.de）
        // 导致证书/SNI/网关路由不兼容。
        if (!normalizedHost.endsWith("amazonaws.com")) {
            return true;
        }
        return "localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost);
    }

    private long resolvePresignExpireMinutes() {
        if (storagePresignExpireMinutes == null || storagePresignExpireMinutes <= 0) {
            return 15;
        }
        return storagePresignExpireMinutes;
    }

    private Long resolveObjectSize(GetObjectResponse response) {
        String contentRange = response.contentRange();
        if (contentRange != null) {
            int separator = contentRange.lastIndexOf('/');
            if (separator >= 0 && separator < contentRange.length() - 1) {
                try {
                    return Long.parseLong(contentRange.substring(separator + 1));
                } catch (NumberFormatException ignored) {
                    // Fall back to Content-Length for providers with a non-standard range header.
                }
            }
        }
        return response.contentLength();
    }

    private String normalizedStorageEndpoint() {
        String endpoint = storageEndpoint == null ? "" : storageEndpoint.trim();
        if (endpoint.isBlank()) {
            throw new IllegalStateException("penmate.storage.endpoint 未配置");
        }
        if (!hasScheme(endpoint)) {
            String protocol;
            if (isLocalEndpoint(endpoint)) {
                protocol = Boolean.FALSE.equals(storageSecure) ? "http" : "https";
            } else {
                protocol = "https";
            }
            endpoint = protocol + "://" + endpoint;
        }
        if (endpoint.endsWith("/")) {
            return endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint;
    }

    private boolean hasScheme(String endpoint) {
        return endpoint.matches("^[a-zA-Z][a-zA-Z\\d+.-]*://.*");
    }

    private boolean isLocalEndpoint(String endpoint) {
        String hostPortPath = endpoint;
        int slashIdx = hostPortPath.indexOf('/');
        if (slashIdx >= 0) {
            hostPortPath = hostPortPath.substring(0, slashIdx);
        }
        int colonIdx = hostPortPath.indexOf(':');
        String host = colonIdx >= 0 ? hostPortPath.substring(0, colonIdx) : hostPortPath;
        String normalizedHost = host.toLowerCase();
        return "localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost)
                || "[::1]".equals(normalizedHost);
    }

}

