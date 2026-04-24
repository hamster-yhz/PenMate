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

/**
 * NovelGatewayImpl。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
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

    /**
     * 处理业务请求。
     *
     * @param novelProjectMapper.findAll( 入参：novelProjectMapper.findAll(
     * @return 出参：处理结果
     */
    @Override
    public List<NovelProject> findAllProjects() { return novelProjectMapper.findAll(); }

    /**
     * 处理业务请求。
     *
     * @param novelProjectMapper.findById(projectId 入参：novelProjectMapper.findById(projectId
     * @return 出参：处理结果
     */
    @Override
    public NovelProject findProjectById(Long projectId) { return novelProjectMapper.findByProjectId(projectId); }

    /**
     * 处理业务请求。
     *
     * @param novelProjectMapper.insert(project 入参：novelProjectMapper.insert(project
     * @return 出参：处理结果
     */
    @Override
    public int insertProject(NovelProject project) { return novelProjectMapper.insert(project); }

    /**
     * 更新业务数据。
     *
     * @param novelProjectMapper.update(project 入参：novelProjectMapper.update(project
     * @return 出参：处理结果
     */
    @Override
    public int updateProject(NovelProject project) { return novelProjectMapper.update(project); }

    /**
     * 处理业务请求。
     *
     * @param novelProjectMapper.softDelete(projectId 入参：novelProjectMapper.softDelete(projectId
     * @return 出参：处理结果
     */
    @Override
    public int softDeleteProject(Long projectId) { return novelProjectMapper.softDelete(projectId); }

    /**
     * 处理业务请求。
     *
     * @param novelVolumeMapper.findByProjectId(projectId 入参：novelVolumeMapper.findByProjectId(projectId
     * @return 出参：处理结果
     */
    @Override
    public List<NovelVolume> findVolumesByProjectId(Long projectId) { return novelVolumeMapper.findByProjectId(projectId); }

    /**
     * 处理业务请求。
     *
     * @param novelVolumeMapper.insert(volume 入参：novelVolumeMapper.insert(volume
     * @return 出参：处理结果
     */
    @Override
    public int insertVolume(NovelVolume volume) { return novelVolumeMapper.insert(volume); }

    /**
     * 更新业务数据。
     *
     * @param novelVolumeMapper.update(volume 入参：novelVolumeMapper.update(volume
     * @return 出参：处理结果
     */
    @Override
    public int updateVolume(NovelVolume volume) { return novelVolumeMapper.update(volume); }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param volumeId 入参：volumeId
     * @return 出参：处理结果
     */
    @Override
    public int softDeleteVolume(Long projectId, Long volumeId) { return novelVolumeMapper.softDelete(projectId, volumeId); }

    /**
     * 处理业务请求。
     *
     * @param novelChapterMapper.findByProjectId(projectId 入参：novelChapterMapper.findByProjectId(projectId
     * @return 出参：处理结果
     */
    @Override
    public List<NovelChapter> findChaptersByProjectId(Long projectId) { return novelChapterMapper.findByProjectId(projectId); }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @return 出参：处理结果
     */
    @Override
    public NovelChapter findChapterByIdAndProjectId(Long projectId, Long chapterId) { return novelChapterMapper.findByIdAndProjectId(projectId, chapterId); }

    /**
     * 处理业务请求。
     *
     * @param novelChapterMapper.insert(chapter 入参：novelChapterMapper.insert(chapter
     * @return 出参：处理结果
     */
    @Override
    public int insertChapter(NovelChapter chapter) { return novelChapterMapper.insert(chapter); }

    /**
     * 更新业务数据。
     *
     * @param novelChapterMapper.update(chapter 入参：novelChapterMapper.update(chapter
     * @return 出参：处理结果
     */
    @Override
    public int updateChapter(NovelChapter chapter) { return novelChapterMapper.update(chapter); }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @return 出参：处理结果
     */
    @Override
    public int softDeleteChapter(Long projectId, Long chapterId) { return novelChapterMapper.softDelete(projectId, chapterId); }

    /**
     * 发布业务状态。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @return 出参：处理结果
     */
    @Override
    public int publishChapter(Long projectId, Long chapterId) { return novelChapterMapper.publish(projectId, chapterId); }

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param objectKey 入参：objectKey
     * @param etag 入参：etag
     * @param size 入参：size
     * @param checksum 入参：checksum
     * @param storageProvider 入参：storageProvider
     * @return 出参：处理结果
     */
    @Override
    public int updateChapterContentMeta(Long projectId, Long chapterId, String objectKey, String etag, Long size, String checksum, String storageProvider) {
        return novelChapterMapper.updateContentMeta(projectId, chapterId, objectKey, etag, size, checksum, storageProvider);
    }

    /**
     * 处理业务请求。
     *
     * @param novelMemberMapper.findByProjectId(projectId 入参：novelMemberMapper.findByProjectId(projectId
     * @return 出参：处理结果
     */
    @Override
    public List<NovelMember> findMembersByProjectId(Long projectId) { return novelMemberMapper.findByProjectId(projectId); }

    /**
     * 处理业务请求。
     *
     * @param novelMemberMapper.insert(member 入参：novelMemberMapper.insert(member
     * @return 出参：处理结果
     */
    @Override
    public int insertMember(NovelMember member) { return novelMemberMapper.insert(member); }

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param userId 入参：userId
     * @param memberRole 入参：memberRole
     * @return 出参：处理结果
     */
    @Override
    public int updateMemberRole(Long projectId, Long userId, String memberRole) { return novelMemberMapper.updateRole(projectId, userId, memberRole); }

    /**
     * 删除业务数据。
     *
     * @param projectId 入参：projectId
     * @param userId 入参：userId
     * @return 出参：处理结果
     */
    @Override
    public int deleteMember(Long projectId, Long userId) { return novelMemberMapper.delete(projectId, userId); }

    /**
     * 处理业务请求。
     *
     * @param novelChapterVersionMapper.findByChapterId(chapterId 入参：novelChapterVersionMapper.findByChapterId(chapterId
     * @return 出参：处理结果
     */
    @Override
    public List<NovelChapterVersion> findVersionsByChapterId(Long chapterId) { return novelChapterVersionMapper.findByChapterId(chapterId); }

    /**
     * 处理业务请求。
     *
     * @param novelChapterVersionMapper.maxVersionNo(chapterId 入参：novelChapterVersionMapper.maxVersionNo(chapterId
     * @return 出参：处理结果
     */
    @Override
    public Integer maxVersionNo(Long chapterId) { return novelChapterVersionMapper.maxVersionNo(chapterId); }

    /**
     * 处理业务请求。
     *
     * @param novelChapterVersionMapper.insert(version 入参：novelChapterVersionMapper.insert(version
     * @return 出参：处理结果
     */
    @Override
    public int insertChapterVersion(NovelChapterVersion version) { return novelChapterVersionMapper.insert(version); }

    /**
     * 处理业务请求。
     *
     * @param chapterId 入参：chapterId
     * @param versionNo 入参：versionNo
     * @return 出参：处理结果
     */
    @Override
    public NovelChapterVersion findVersionByChapterAndVersion(Long chapterId, Integer versionNo) { return novelChapterVersionMapper.findByChapterAndVersion(chapterId, versionNo); }

    /**
     * 处理业务请求。
     *
     * @param novelOutlineNodeMapper.findByProjectId(projectId 入参：novelOutlineNodeMapper.findByProjectId(projectId
     * @return 出参：处理结果
     */
    @Override
    public List<NovelOutlineNode> findOutlineNodesByProjectId(Long projectId) { return novelOutlineNodeMapper.findByProjectId(projectId); }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param nodeId 入参：nodeId
     * @return 出参：处理结果
     */
    @Override
    public NovelOutlineNode findOutlineNodeByIdAndProjectId(Long projectId, Long nodeId) { return novelOutlineNodeMapper.findByIdAndProjectId(projectId, nodeId); }

    /**
     * 处理业务请求。
     *
     * @param novelOutlineNodeMapper.insert(node 入参：novelOutlineNodeMapper.insert(node
     * @return 出参：处理结果
     */
    @Override
    public int insertOutlineNode(NovelOutlineNode node) { return novelOutlineNodeMapper.insert(node); }

    /**
     * 更新业务数据。
     *
     * @param novelOutlineNodeMapper.update(node 入参：novelOutlineNodeMapper.update(node
     * @return 出参：处理结果
     */
    @Override
    public int updateOutlineNode(NovelOutlineNode node) { return novelOutlineNodeMapper.update(node); }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param nodeId 入参：nodeId
     * @param parentId 入参：parentId
     * @param sortOrder 入参：sortOrder
     * @return 出参：处理结果
     */
    @Override
    public int moveOutlineNode(Long projectId, Long nodeId, Long parentId, Integer sortOrder) { return novelOutlineNodeMapper.move(projectId, nodeId, parentId, sortOrder); }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param nodeId 入参：nodeId
     * @return 出参：处理结果
     */
    @Override
    public int softDeleteOutlineNode(Long projectId, Long nodeId) { return novelOutlineNodeMapper.softDelete(projectId, nodeId); }

    /**
     * 处理业务请求。
     *
     * @param novelCardMapper.findByProjectId(projectId 入参：novelCardMapper.findByProjectId(projectId
     * @return 出参：处理结果
     */
    @Override
    public List<NovelCard> findCardsByProjectId(Long projectId) { return novelCardMapper.findByProjectId(projectId); }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param cardId 入参：cardId
     * @return 出参：处理结果
     */
    @Override
    public NovelCard findCardByIdAndProjectId(Long projectId, Long cardId) { return novelCardMapper.findByIdAndProjectId(projectId, cardId); }

    /**
     * 处理业务请求。
     *
     * @param novelCardMapper.insert(card 入参：novelCardMapper.insert(card
     * @return 出参：处理结果
     */
    @Override
    public int insertCard(NovelCard card) { return novelCardMapper.insert(card); }

    /**
     * 更新业务数据。
     *
     * @param novelCardMapper.update(card 入参：novelCardMapper.update(card
     * @return 出参：处理结果
     */
    @Override
    public int updateCard(NovelCard card) { return novelCardMapper.update(card); }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param cardId 入参：cardId
     * @return 出参：处理结果
     */
    @Override
    public int softDeleteCard(Long projectId, Long cardId) { return novelCardMapper.softDelete(projectId, cardId); }

    /**
     * 处理业务请求。
     *
     * @param novelCardRelationMapper.findByProjectId(projectId 入参：novelCardRelationMapper.findByProjectId(projectId
     * @return 出参：处理结果
     */
    @Override
    public List<NovelCardRelation> findCardRelationsByProjectId(Long projectId) { return novelCardRelationMapper.findByProjectId(projectId); }

    /**
     * 处理业务请求。
     *
     * @param novelCardRelationMapper.insert(relation 入参：novelCardRelationMapper.insert(relation
     * @return 出参：处理结果
     */
    @Override
    public int insertCardRelation(NovelCardRelation relation) { return novelCardRelationMapper.insert(relation); }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param relationId 入参：relationId
     * @return 出参：处理结果
     */
    @Override
    public int softDeleteCardRelation(Long projectId, Long relationId) { return novelCardRelationMapper.softDelete(projectId, relationId); }
}

