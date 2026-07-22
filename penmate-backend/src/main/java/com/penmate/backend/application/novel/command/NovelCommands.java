package com.penmate.backend.application.novel.command;

public final class NovelCommands {

    private NovelCommands() {
    }

    /**
     * CreateProjectCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record CreateProjectCommand(Long ownerUserId, String title, String summary, String genre,
                                       String customGenre, java.util.List<String> tags, Integer status) {
        public CreateProjectCommand(Long ownerUserId, String title, String summary, Integer status) {
            this(ownerUserId, title, summary, null, null, java.util.List.of(), status);
        }
    }

    /**
     * UpdateProjectCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record UpdateProjectCommand(String title, String summary, String genre,
                                       String customGenre, java.util.List<String> tags, Integer status) {
        public UpdateProjectCommand(String title, String summary, Integer status) {
            this(title, summary, null, null, java.util.List.of(), status);
        }
    }

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
    public record CreateChapterCommand(Long volumeId, String title, Integer sortOrder) {}

    public record ImportProjectCommand(CreateProjectCommand project, java.util.List<ImportVolumeCommand> volumes) {}

    public record ImportVolumeCommand(String title, java.util.List<ImportChapterCommand> chapters) {}

    public record ImportChapterCommand(String title, String content) {}

    /**
     * UpdateChapterCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record UpdateChapterCommand(String title) {}

    public enum DirectoryNodeType {
        VOLUME,
        CHAPTER
    }

    public record MoveDirectoryItemCommand(DirectoryNodeType nodeType, Long nodeId, Long targetVolumeId,
                                           Integer sortOrder, Long expectedStructureRevision) {}

    /**
     * CreateCardCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */

    /**
     * UpdateCardCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */

    /**
     * CreateCardRelationCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
}

