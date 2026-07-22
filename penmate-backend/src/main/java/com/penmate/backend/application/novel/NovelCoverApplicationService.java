package com.penmate.backend.application.novel;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.ops.AsyncJobQueueService;
import com.penmate.backend.domain.novel.model.NovelCoverUploadSession;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelCoverUploadRepository;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.novel.service.NovelCoverImageProcessor;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class NovelCoverApplicationService {
    private static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Set<String> MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final NovelGateway novels;
    private final NovelCoverUploadRepository uploads;
    private final BusinessIdGenerator ids;
    private final ObjectStorageService storage;
    private final NovelCoverImageProcessor images;
    private final AsyncJobQueueService jobs;
    private final JsonCodec jsonCodec;
    private final NovelCoverCommitService commits;

    public NovelCoverApplicationService(NovelGateway novels, NovelCoverUploadRepository uploads,
                                        BusinessIdGenerator ids, ObjectStorageService storage,
                                        NovelCoverImageProcessor images, AsyncJobQueueService jobs,
                                        JsonCodec jsonCodec, NovelCoverCommitService commits) {
        this.novels = novels;
        this.uploads = uploads;
        this.ids = ids;
        this.storage = storage;
        this.images = images;
        this.jobs = jobs;
        this.jsonCodec = jsonCodec;
        this.commits = commits;
    }

    @Transactional
    public UploadInitialization initializeUpload(Long projectId, Long actorUserId, UploadRequest request) {
        requireOwner(projectId, actorUserId);
        String filename = requireText(request.filename(), "filename");
        String extension = extension(filename);
        String mimeType = normalizeMime(request.mimeType());
        validateDeclaration(extension, mimeType, request.size());
        long uploadId = ids.nextId();
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        String objectKey = "novels/%d/covers/uploads/%d.%s".formatted(projectId, uploadId,
                "jpeg".equals(extension) ? "jpg" : extension);
        NovelCoverUploadSession session = new NovelCoverUploadSession();
        session.setUploadId(uploadId);
        session.setProjectId(projectId);
        session.setOwnerUserId(actorUserId);
        session.setOperationType("UPLOAD");
        session.setOriginalFilename(filename);
        session.setDeclaredMimeType(mimeType);
        session.setExpectedSize(request.size());
        session.setExpectedChecksum(normalizeChecksum(request.sha256()));
        session.setOriginalObjectKey(objectKey);
        session.setUploadTokenHash(sha256(token.getBytes(StandardCharsets.UTF_8)));
        session.setStatus("PENDING");
        session.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        if (uploads.insert(session) != 1) throw BusinessException.of("Failed to initialize cover upload");
        return new UploadInitialization(String.valueOf(uploadId), token, storage.buildUploadUrl(objectKey, mimeType),
                session.getExpiresAt());
    }

    @Transactional
    public CoverState completeUpload(Long projectId, Long actorUserId, Long uploadId,
                                     String uploadToken, CropRequest crop) {
        NovelCoverUploadSession session = requireSessionForUpdate(projectId, actorUserId, uploadId);
        if (!"PENDING".equals(session.getStatus())) throw BusinessException.conflict("Cover upload is not pending");
        if (session.getExpiresAt().isBefore(Instant.now())) throw BusinessException.conflict("Cover upload has expired");
        verifyToken(session, uploadToken);
        ObjectStorageService.ObjectMetadata metadata = storage.head(session.getOriginalObjectKey());
        if (metadata.size() == null || !metadata.size().equals(session.getExpectedSize())
                || metadata.size() <= 0 || metadata.size() > MAX_UPLOAD_BYTES) {
            throw BusinessException.badRequest("Uploaded cover size does not match the initialized upload");
        }
        if (!session.getDeclaredMimeType().equals(normalizeMime(metadata.contentType()))) {
            throw BusinessException.badRequest("Uploaded cover content type does not match");
        }
        byte[] source = storage.readBytes(session.getOriginalObjectKey());
        String checksum = sha256(source);
        if (session.getExpectedChecksum() != null && !session.getExpectedChecksum().equals(checksum)) {
            throw BusinessException.badRequest("Uploaded cover checksum does not match");
        }
        NovelCoverImageProcessor.ImageInfo info = images.inspect(source);
        CropRequest normalized = validateCrop(crop);
        startProcessing(session, normalized, info);
        return getState(projectId, actorUserId);
    }

    @Transactional
    public CoverState recrop(Long projectId, Long actorUserId, CropRequest crop) {
        NovelProject project = requireOwner(projectId, actorUserId);
        if (project.getCoverOriginalObjectKey() == null || project.getCoverOriginalObjectKey().isBlank()) {
            throw BusinessException.conflict("This project has no uploaded cover to crop");
        }
        CropRequest normalized = validateCrop(crop);
        byte[] source = storage.readBytes(project.getCoverOriginalObjectKey());
        NovelCoverImageProcessor.ImageInfo info = images.inspect(source);
        NovelCoverUploadSession session = new NovelCoverUploadSession();
        session.setUploadId(ids.nextId());
        session.setProjectId(projectId);
        session.setOwnerUserId(actorUserId);
        session.setOperationType("RECROP");
        session.setDeclaredMimeType("application/octet-stream");
        session.setOriginalObjectKey(project.getCoverOriginalObjectKey());
        session.setStatus("PENDING");
        session.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        if (uploads.insert(session) != 1) throw BusinessException.of("Failed to initialize cover crop");
        startProcessing(session, normalized, info);
        return getState(projectId, actorUserId);
    }

    @Transactional
    public CoverState retry(Long projectId, Long actorUserId, Long uploadId) {
        NovelCoverUploadSession session = requireSessionForUpdate(projectId, actorUserId, uploadId);
        NovelProject project = uploads.lockProject(projectId);
        if (!"FAILED".equals(session.getStatus()) || project == null
                || !uploadId.equals(project.getCoverPendingUploadId())) {
            throw BusinessException.conflict("Cover processing cannot be retried");
        }
        if (uploads.retry(uploadId) != 1) throw BusinessException.conflict("Cover processing state changed");
        enqueue(session);
        return state(project, uploads.findById(uploadId));
    }

    public CoverState getState(Long projectId, Long actorUserId) {
        NovelProject project = requireOwner(projectId, actorUserId);
        NovelCoverUploadSession session = project.getCoverPendingUploadId() == null
                ? uploads.findLatestCompletedByProject(projectId) : uploads.findById(project.getCoverPendingUploadId());
        return state(project, session);
    }

    @Transactional
    public void remove(Long projectId, Long actorUserId) {
        NovelProject project = uploads.lockProject(projectId);
        if (project == null || !Objects.equals(project.getOwnerUserId(), actorUserId)) {
            throw BusinessException.notFound("Novel project not found");
        }
        if (project.getCoverPendingUploadId() != null) uploads.markSuperseded(project.getCoverPendingUploadId());
        if (uploads.clearCover(projectId, actorUserId) != 1) throw BusinessException.notFound("Novel project not found");
        deleteQuietly(Stream.of(project.getCoverOriginalObjectKey(), project.getCoverDisplayObjectKey(),
                project.getCoverThumbnailObjectKey()).filter(Objects::nonNull).toList());
    }

    public void process(Long uploadId) {
        NovelCoverUploadSession session = uploads.findById(uploadId);
        if (session == null || !"PROCESSING".equals(session.getStatus())) return;
        NovelCoverImageProcessor.Crop crop = new NovelCoverImageProcessor.Crop(session.getCropX(), session.getCropY(),
                session.getCropWidth(), session.getCropHeight());
        NovelCoverImageProcessor.GeneratedImages generated = images.cropToWebp(
                storage.readBytes(session.getOriginalObjectKey()), crop);
        storage.putBytes(session.getDisplayObjectKey(), generated.display(), "image/webp");
        storage.putBytes(session.getThumbnailObjectKey(), generated.thumbnail(), "image/webp");
        NovelCoverCommitService.CommitResult result = commits.commit(uploadId);
        if (result.applied()) {
            deleteQuietly(result.replacedObjectKeys());
        } else {
            deleteQuietly(List.of(session.getDisplayObjectKey(), session.getThumbnailObjectKey()));
        }
    }

    @Transactional
    public void markFailed(Long uploadId, Throwable error) {
        String message = error == null || error.getMessage() == null ? "Cover processing failed" : error.getMessage();
        uploads.markFailed(uploadId, "COVER_PROCESSING_FAILED", message.substring(0, Math.min(500, message.length())));
    }

    public NovelProject decorate(NovelProject project) {
        if (project == null) return null;
        project.setCoverUrl(readUrl(project.getCoverDisplayObjectKey()));
        project.setCoverThumbnailUrl(readUrl(project.getCoverThumbnailObjectKey()));
        return project;
    }

    @Transactional
    public int purgeExpiredPendingUploads() {
        int purged = 0;
        for (NovelCoverUploadSession session : uploads.findExpiredPending(Instant.now())) {
            if (uploads.markExpired(session.getUploadId()) != 1) continue;
            deleteQuietly(List.of(session.getOriginalObjectKey()));
            purged++;
        }
        return purged;
    }

    private void startProcessing(NovelCoverUploadSession session, CropRequest crop,
                                 NovelCoverImageProcessor.ImageInfo info) {
        NovelProject project = uploads.lockProject(session.getProjectId());
        if (project == null || !Objects.equals(project.getOwnerUserId(), session.getOwnerUserId())) {
            throw BusinessException.notFound("Novel project not found");
        }
        if (project.getCoverPendingUploadId() != null
                && !project.getCoverPendingUploadId().equals(session.getUploadId())) {
            NovelCoverUploadSession previous = uploads.findById(project.getCoverPendingUploadId());
            uploads.markSuperseded(project.getCoverPendingUploadId());
            if (previous != null) {
                deleteQuietly(Stream.of(previous.getDisplayObjectKey(), previous.getThumbnailObjectKey(),
                                Objects.equals(previous.getOriginalObjectKey(), project.getCoverOriginalObjectKey())
                                        ? null : previous.getOriginalObjectKey())
                        .filter(Objects::nonNull).toList());
            }
        }
        String displayKey = "novels/%d/covers/generated/%d-display.webp".formatted(session.getProjectId(), session.getUploadId());
        String thumbnailKey = "novels/%d/covers/generated/%d-thumbnail.webp".formatted(session.getProjectId(), session.getUploadId());
        if (uploads.markProcessing(session.getUploadId(), crop.x(), crop.y(), crop.width(), crop.height(),
                info.width(), info.height(), displayKey, thumbnailKey) != 1
                || uploads.setPendingUpload(session.getProjectId(), session.getOwnerUserId(), session.getUploadId()) != 1) {
            throw BusinessException.conflict("Cover upload state changed");
        }
        session.setDisplayObjectKey(displayKey);
        session.setThumbnailObjectKey(thumbnailKey);
        enqueue(session);
    }

    private void enqueue(NovelCoverUploadSession session) {
        jobs.enqueue("NOVEL_COVER_PROCESS", "novel:cover:%d:%s".formatted(session.getUploadId(), UUID.randomUUID()),
                session.getOwnerUserId(), session.getProjectId(),
                jsonCodec.write(Map.of("uploadId", session.getUploadId())));
    }

    private CoverState state(NovelProject project, NovelCoverUploadSession pending) {
        String status = pending == null
                ? (project.getCoverDisplayObjectKey() == null ? "EMPTY" : "READY")
                : ("COMPLETED".equals(pending.getStatus()) ? "READY" : pending.getStatus());
        return new CoverState(readUrl(project.getCoverDisplayObjectKey()),
                readUrl(project.getCoverThumbnailObjectKey()), readUrl(project.getCoverOriginalObjectKey()),
                pending == null ? null : String.valueOf(pending.getUploadId()), status,
                pending == null ? null : pending.getErrorMessage(),
                pending == null ? null : new CropRequest(pending.getCropX(), pending.getCropY(),
                        pending.getCropWidth(), pending.getCropHeight()));
    }

    private String readUrl(String key) {
        return key == null || key.isBlank() ? null : storage.buildReadUrl(key);
    }

    private NovelCoverUploadSession requireSessionForUpdate(Long projectId, Long actorUserId, Long uploadId) {
        NovelCoverUploadSession session = uploads.findByIdForUpdate(uploadId);
        if (session == null || !Objects.equals(session.getProjectId(), projectId)
                || !Objects.equals(session.getOwnerUserId(), actorUserId)) {
            throw BusinessException.notFound("Cover upload not found");
        }
        return session;
    }

    private NovelProject requireOwner(Long projectId, Long actorUserId) {
        NovelProject project = novels.findProjectById(projectId);
        if (project == null || !Objects.equals(project.getOwnerUserId(), actorUserId)) {
            throw BusinessException.notFound("Novel project not found");
        }
        return project;
    }

    private CropRequest validateCrop(CropRequest crop) {
        if (crop == null || crop.x() == null || crop.y() == null || crop.width() == null || crop.height() == null) {
            throw BusinessException.badRequest("Cover crop is required");
        }
        double x = crop.x();
        double y = crop.y();
        double width = crop.width();
        double height = crop.height();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)
                || x < 0 || y < 0 || width <= 0 || height <= 0 || x + width > 1.000001 || y + height > 1.000001
                || Math.abs((width / height) - (2d / 3d)) > 0.01) {
            throw BusinessException.badRequest("Cover crop must be inside the image with a 2:3 ratio");
        }
        return new CropRequest(x, y, width, height);
    }

    private void validateDeclaration(String extension, String mimeType, Long size) {
        if (!EXTENSIONS.contains(extension) || !MIME_TYPES.contains(mimeType)) {
            throw BusinessException.badRequest("Only JPG, PNG, WebP, and GIF cover images are supported");
        }
        boolean matches = switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg".equals(mimeType);
            default -> ("image/" + extension).equals(mimeType);
        };
        if (!matches) throw BusinessException.badRequest("Cover filename extension and MIME type do not match");
        if (size == null || size <= 0 || size > MAX_UPLOAD_BYTES) {
            throw BusinessException.badRequest("Cover size must be between 1 byte and 10 MiB");
        }
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 1 || index == filename.length() - 1) throw BusinessException.badRequest("Cover filename needs an extension");
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeMime(String value) {
        if (value == null) return "";
        String normalized = value.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private String normalizeChecksum(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) throw BusinessException.badRequest("sha256 must be hexadecimal");
        return normalized;
    }

    private void verifyToken(NovelCoverUploadSession session, String token) {
        if (token == null || !MessageDigest.isEqual(session.getUploadTokenHash().getBytes(StandardCharsets.US_ASCII),
                sha256(token.getBytes(StandardCharsets.UTF_8)).getBytes(StandardCharsets.US_ASCII))) {
            throw BusinessException.forbidden("Invalid cover upload token");
        }
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw BusinessException.badRequest(field + " is required");
        return value.strip();
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void deleteQuietly(List<String> keys) {
        keys.stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).distinct().forEach(key -> {
            try { storage.delete(key); } catch (RuntimeException ignored) { /* storage lifecycle removes orphans */ }
        });
    }

    public record UploadRequest(String filename, String mimeType, Long size, String sha256) { }
    public record UploadInitialization(String uploadId, String uploadToken, String uploadUrl, Instant expiresAt) { }
    public record CropRequest(Double x, Double y, Double width, Double height) { }
    public record CoverState(String coverUrl, String thumbnailUrl, String originalUrl, String uploadId,
                             String status, String errorMessage, CropRequest crop) { }
}
