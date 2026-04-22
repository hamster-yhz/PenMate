package com.penmate.backend.application.novel.command;

public final class NovelCommands {

    private NovelCommands() {
    }

    /**
     * CreateProjectCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record CreateProjectCommand(Long ownerUserId, String title, String summary, Integer status) {}

    /**
     * UpdateProjectCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record UpdateProjectCommand(String title, String summary, Integer status) {}

    /**
     * CreateVolumeCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record CreateVolumeCommand(String title, Integer sortOrder, String description) {}

    /**
     * UpdateVolumeCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record UpdateVolumeCommand(String title, Integer sortOrder, String description) {}

    /**
     * CreateChapterCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record CreateChapterCommand(Long volumeId,
                                       Long outlineNodeId,
                                       String title,
                                       Integer chapterNo,
                                       Integer status,
                                       Integer wordCount,
                                       String excerpt,
                                       String contentObjectKey,
                                       String contentEtag,
                                       Long contentSize,
                                       String contentChecksum,
                                       String storageProvider) {}

    /**
     * UpdateChapterCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record UpdateChapterCommand(Long volumeId,
                                       Long outlineNodeId,
                                       String title,
                                       Integer chapterNo,
                                       Integer status,
                                       Integer wordCount,
                                       String excerpt,
                                       String contentObjectKey,
                                       String contentEtag,
                                       Long contentSize,
                                       String contentChecksum,
                                       String storageProvider) {}

    /**
     * AddMemberCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record AddMemberCommand(Long userId, String memberRole) {}

    /**
     * UpdateMemberCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record UpdateMemberCommand(String memberRole) {}

    /**
     * CreateChapterVersionCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record CreateChapterVersionCommand(String changeType, String changeReason, Long createdBy) {}

    /**
     * CommitChapterContentCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record CommitChapterContentCommand(String objectKey,
                                              String etag,
                                              Long size,
                                              String checksum,
                                              String storageProvider,
                                              String content) {}

    /**
     * CreateOutlineNodeCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record CreateOutlineNodeCommand(Long parentId,
                                           String title,
                                           String nodeType,
                                           Integer sortOrder,
                                           String content) {}

    /**
     * UpdateOutlineNodeCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record UpdateOutlineNodeCommand(Long parentId,
                                           String title,
                                           String nodeType,
                                           Integer sortOrder,
                                           String content) {}

    /**
     * MoveOutlineNodeCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record MoveOutlineNodeCommand(Long parentId, Integer sortOrder) {}

    /**
     * CreateCardCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record CreateCardCommand(String cardType, String name, String summary, String detailJson) {}

    /**
     * UpdateCardCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record UpdateCardCommand(String cardType, String name, String summary, String detailJson) {}

    /**
     * CreateCardRelationCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record CreateCardRelationCommand(Long fromCardId,
                                            Long toCardId,
                                            String relationType,
                                            String description) {}
}

