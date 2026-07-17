package com.penmate.backend.domain.novel.repository;

import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelChapterVersion;
import com.penmate.backend.domain.novel.model.NovelMember;
import com.penmate.backend.domain.novel.model.NovelOutlineNode;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;

import java.util.List;

public interface NovelGateway {

    List<NovelProject> findAllProjects();

    NovelProject findProjectById(Long projectId);

    int insertProject(NovelProject project);

    int updateProject(NovelProject project);

    int incrementStructureRevision(Long projectId);

    int softDeleteProject(Long projectId);

    List<NovelVolume> findVolumesByProjectId(Long projectId);

    int insertVolume(NovelVolume volume);

    int updateVolume(NovelVolume volume);

    int softDeleteVolume(Long projectId, Long volumeId);

    List<NovelChapter> findChaptersByProjectId(Long projectId);

    NovelChapter findChapterByIdAndProjectId(Long projectId, Long chapterId);

    int insertChapter(NovelChapter chapter);

    int updateChapter(NovelChapter chapter);

    int softDeleteChapter(Long projectId, Long chapterId);

    int publishChapter(Long projectId, Long chapterId);

    int updateChapterContentMeta(Long projectId,
                                 Long chapterId,
                                 String objectKey,
                                 String etag,
                                 Long size,
                                 String checksum,
                                 String storageProvider);

    List<NovelMember> findMembersByProjectId(Long projectId);

    int insertMember(NovelMember member);

    int updateMemberRole(Long projectId, Long userId, String memberRole);

    int deleteMember(Long projectId, Long userId);

    List<NovelChapterVersion> findVersionsByChapterId(Long chapterId);

    Integer maxVersionNo(Long chapterId);

    int insertChapterVersion(NovelChapterVersion version);

    NovelChapterVersion findVersionByChapterAndVersion(Long chapterId, Integer versionNo);

    List<NovelOutlineNode> findOutlineNodesByProjectId(Long projectId);

    NovelOutlineNode findOutlineNodeByIdAndProjectId(Long projectId, Long nodeId);

    int insertOutlineNode(NovelOutlineNode node);

    int updateOutlineNode(NovelOutlineNode node);

    int moveOutlineNode(Long projectId, Long nodeId, Long parentId, Integer sortOrder);

    int softDeleteOutlineNode(Long projectId, Long nodeId);

}

