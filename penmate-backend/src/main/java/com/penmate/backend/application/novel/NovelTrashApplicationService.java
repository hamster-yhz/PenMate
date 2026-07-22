package com.penmate.backend.application.novel;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class NovelTrashApplicationService {

    public static final Duration RETENTION = Duration.ofDays(30);

    private final NovelGateway novelGateway;
    private final ObjectStorageService objectStorage;

    public NovelTrashApplicationService(NovelGateway novelGateway, ObjectStorageService objectStorage) {
        this.novelGateway = novelGateway;
        this.objectStorage = objectStorage;
    }

    public List<NovelProject> listDeletedProjects(Long ownerUserId) {
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        return novelGateway.findDeletedProjectsByOwner(ownerUserId);
    }

    @Transactional
    public NovelProject restoreProject(Long projectId, Long ownerUserId) {
        requireDeletedProject(projectId, ownerUserId);
        if (novelGateway.restoreProject(projectId, ownerUserId) != 1) {
            throw BusinessException.conflict("Project is no longer available to restore");
        }
        NovelProject restored = novelGateway.findProjectById(projectId);
        if (restored == null) {
            throw BusinessException.of("Failed to restore project");
        }
        return restored;
    }

    @Transactional
    public void permanentlyDeleteProject(Long projectId, Long ownerUserId, String confirmationTitle) {
        NovelProject project = novelGateway.lockDeletedProject(projectId, ownerUserId, null);
        if (project == null) {
            throw BusinessException.notFound("Deleted project not found");
        }
        if (!project.getTitle().equals(confirmationTitle)) {
            throw BusinessException.badRequest("Project title confirmation does not match");
        }
        purgeLockedProject(project, ownerUserId, null);
    }

    @Transactional
    public int purgeExpiredProjects() {
        Instant cutoff = Instant.now().minus(RETENTION);
        int purged = 0;
        for (Long projectId : novelGateway.findExpiredDeletedProjectIds(cutoff)) {
            NovelProject project = novelGateway.lockDeletedProject(projectId, null, cutoff);
            if (project == null) continue;
            purgeLockedProject(project, null, cutoff);
            purged++;
        }
        if (purged > 0) log.info("Purged expired projects from trash: count={}", purged);
        return purged;
    }

    @Transactional
    public int purgeAllProjectsForAccount(Long ownerUserId) {
        int purged = 0;
        for (Long projectId : novelGateway.findProjectIdsByOwner(ownerUserId)) {
            novelGateway.softDeleteProject(projectId, ownerUserId);
            NovelProject project = novelGateway.lockDeletedProject(projectId, ownerUserId, null);
            if (project == null) continue;
            purgeLockedProject(project, ownerUserId, null);
            purged++;
        }
        return purged;
    }

    private NovelProject requireDeletedProject(Long projectId, Long ownerUserId) {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        NovelProject project = novelGateway.findDeletedProjectByIdAndOwner(projectId, ownerUserId);
        if (project == null) throw BusinessException.notFound("Deleted project not found");
        return project;
    }

    private void purgeLockedProject(NovelProject project, Long ownerUserId, Instant deletedBefore) {
        List<String> objectKeys = novelGateway.findProjectObjectKeys(project.getProjectId());
        for (String objectKey : objectKeys) objectStorage.delete(objectKey);
        if (novelGateway.purgeDeletedProject(project.getProjectId(), ownerUserId, deletedBefore) != 1) {
            throw BusinessException.conflict("Project is no longer available to delete");
        }
    }
}
