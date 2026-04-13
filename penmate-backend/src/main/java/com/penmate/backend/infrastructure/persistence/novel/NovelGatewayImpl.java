package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelCard;
import com.penmate.backend.domain.novel.model.NovelCardRelation;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelChapterVersion;
import com.penmate.backend.domain.novel.model.NovelMember;
import com.penmate.backend.domain.novel.model.NovelOutlineNode;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class NovelGatewayImpl implements NovelGateway {

    private final NovelProjectMapper novelProjectMapper;
    private final NovelVolumeMapper novelVolumeMapper;
    private final NovelChapterMapper novelChapterMapper;
    private final NovelMemberMapper novelMemberMapper;
    private final NovelChapterVersionMapper novelChapterVersionMapper;
    private final NovelOutlineNodeMapper novelOutlineNodeMapper;
    private final NovelCardMapper novelCardMapper;
    private final NovelCardRelationMapper novelCardRelationMapper;

    public NovelGatewayImpl(NovelProjectMapper novelProjectMapper,
                            NovelVolumeMapper novelVolumeMapper,
                            NovelChapterMapper novelChapterMapper,
                            NovelMemberMapper novelMemberMapper,
                            NovelChapterVersionMapper novelChapterVersionMapper,
                            NovelOutlineNodeMapper novelOutlineNodeMapper,
                            NovelCardMapper novelCardMapper,
                            NovelCardRelationMapper novelCardRelationMapper) {
        this.novelProjectMapper = novelProjectMapper;
        this.novelVolumeMapper = novelVolumeMapper;
        this.novelChapterMapper = novelChapterMapper;
        this.novelMemberMapper = novelMemberMapper;
        this.novelChapterVersionMapper = novelChapterVersionMapper;
        this.novelOutlineNodeMapper = novelOutlineNodeMapper;
        this.novelCardMapper = novelCardMapper;
        this.novelCardRelationMapper = novelCardRelationMapper;
    }

    @Override
    public List<NovelProject> findAllProjects() { return novelProjectMapper.findAll(); }

    @Override
    public NovelProject findProjectById(Long projectId) { return novelProjectMapper.findById(projectId); }

    @Override
    public int insertProject(NovelProject project) { return novelProjectMapper.insert(project); }

    @Override
    public int updateProject(NovelProject project) { return novelProjectMapper.update(project); }

    @Override
    public int softDeleteProject(Long projectId) { return novelProjectMapper.softDelete(projectId); }

    @Override
    public List<NovelVolume> findVolumesByProjectId(Long projectId) { return novelVolumeMapper.findByProjectId(projectId); }

    @Override
    public int insertVolume(NovelVolume volume) { return novelVolumeMapper.insert(volume); }

    @Override
    public int updateVolume(NovelVolume volume) { return novelVolumeMapper.update(volume); }

    @Override
    public int softDeleteVolume(Long projectId, Long volumeId) { return novelVolumeMapper.softDelete(projectId, volumeId); }

    @Override
    public List<NovelChapter> findChaptersByProjectId(Long projectId) { return novelChapterMapper.findByProjectId(projectId); }

    @Override
    public NovelChapter findChapterByIdAndProjectId(Long projectId, Long chapterId) { return novelChapterMapper.findByIdAndProjectId(projectId, chapterId); }

    @Override
    public int insertChapter(NovelChapter chapter) { return novelChapterMapper.insert(chapter); }

    @Override
    public int updateChapter(NovelChapter chapter) { return novelChapterMapper.update(chapter); }

    @Override
    public int softDeleteChapter(Long projectId, Long chapterId) { return novelChapterMapper.softDelete(projectId, chapterId); }

    @Override
    public int publishChapter(Long projectId, Long chapterId) { return novelChapterMapper.publish(projectId, chapterId); }

    @Override
    public int updateChapterContentMeta(Long projectId, Long chapterId, String objectKey, String etag, Long size, String checksum, String storageProvider) {
        return novelChapterMapper.updateContentMeta(projectId, chapterId, objectKey, etag, size, checksum, storageProvider);
    }

    @Override
    public List<NovelMember> findMembersByProjectId(Long projectId) { return novelMemberMapper.findByProjectId(projectId); }

    @Override
    public int insertMember(NovelMember member) { return novelMemberMapper.insert(member); }

    @Override
    public int updateMemberRole(Long projectId, Long userId, String memberRole) { return novelMemberMapper.updateRole(projectId, userId, memberRole); }

    @Override
    public int deleteMember(Long projectId, Long userId) { return novelMemberMapper.delete(projectId, userId); }

    @Override
    public List<NovelChapterVersion> findVersionsByChapterId(Long chapterId) { return novelChapterVersionMapper.findByChapterId(chapterId); }

    @Override
    public Integer maxVersionNo(Long chapterId) { return novelChapterVersionMapper.maxVersionNo(chapterId); }

    @Override
    public int insertChapterVersion(NovelChapterVersion version) { return novelChapterVersionMapper.insert(version); }

    @Override
    public NovelChapterVersion findVersionByChapterAndVersion(Long chapterId, Integer versionNo) { return novelChapterVersionMapper.findByChapterAndVersion(chapterId, versionNo); }

    @Override
    public List<NovelOutlineNode> findOutlineNodesByProjectId(Long projectId) { return novelOutlineNodeMapper.findByProjectId(projectId); }

    @Override
    public NovelOutlineNode findOutlineNodeByIdAndProjectId(Long projectId, Long nodeId) { return novelOutlineNodeMapper.findByIdAndProjectId(projectId, nodeId); }

    @Override
    public int insertOutlineNode(NovelOutlineNode node) { return novelOutlineNodeMapper.insert(node); }

    @Override
    public int updateOutlineNode(NovelOutlineNode node) { return novelOutlineNodeMapper.update(node); }

    @Override
    public int moveOutlineNode(Long projectId, Long nodeId, Long parentId, Integer sortOrder) { return novelOutlineNodeMapper.move(projectId, nodeId, parentId, sortOrder); }

    @Override
    public int softDeleteOutlineNode(Long projectId, Long nodeId) { return novelOutlineNodeMapper.softDelete(projectId, nodeId); }

    @Override
    public List<NovelCard> findCardsByProjectId(Long projectId) { return novelCardMapper.findByProjectId(projectId); }

    @Override
    public NovelCard findCardByIdAndProjectId(Long projectId, Long cardId) { return novelCardMapper.findByIdAndProjectId(projectId, cardId); }

    @Override
    public int insertCard(NovelCard card) { return novelCardMapper.insert(card); }

    @Override
    public int updateCard(NovelCard card) { return novelCardMapper.update(card); }

    @Override
    public int softDeleteCard(Long projectId, Long cardId) { return novelCardMapper.softDelete(projectId, cardId); }

    @Override
    public List<NovelCardRelation> findCardRelationsByProjectId(Long projectId) { return novelCardRelationMapper.findByProjectId(projectId); }

    @Override
    public int insertCardRelation(NovelCardRelation relation) { return novelCardRelationMapper.insert(relation); }

    @Override
    public int softDeleteCardRelation(Long projectId, Long relationId) { return novelCardRelationMapper.softDelete(projectId, relationId); }
}

