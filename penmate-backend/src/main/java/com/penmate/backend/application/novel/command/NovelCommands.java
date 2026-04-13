package com.penmate.backend.application.novel.command;

public final class NovelCommands {

    private NovelCommands() {
    }

    public record CreateProjectCommand(Long ownerUserId, String title, String summary, Integer status) {}

    public record UpdateProjectCommand(String title, String summary, Integer status) {}

    public record CreateVolumeCommand(String title, Integer sortOrder, String description) {}

    public record UpdateVolumeCommand(String title, Integer sortOrder, String description) {}

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

    public record AddMemberCommand(Long userId, String memberRole) {}

    public record UpdateMemberCommand(String memberRole) {}

    public record CreateChapterVersionCommand(String changeType, String changeReason, Long createdBy) {}

    public record CommitChapterContentCommand(String objectKey,
                                              String etag,
                                              Long size,
                                              String checksum,
                                              String storageProvider) {}

    public record CreateOutlineNodeCommand(Long parentId,
                                           String title,
                                           String nodeType,
                                           Integer sortOrder,
                                           String content) {}

    public record UpdateOutlineNodeCommand(Long parentId,
                                           String title,
                                           String nodeType,
                                           Integer sortOrder,
                                           String content) {}

    public record MoveOutlineNodeCommand(Long parentId, Integer sortOrder) {}

    public record CreateCardCommand(String cardType, String name, String summary, String detailJson) {}

    public record UpdateCardCommand(String cardType, String name, String summary, String detailJson) {}

    public record CreateCardRelationCommand(Long fromCardId,
                                            Long toCardId,
                                            String relationType,
                                            String description) {}
}

