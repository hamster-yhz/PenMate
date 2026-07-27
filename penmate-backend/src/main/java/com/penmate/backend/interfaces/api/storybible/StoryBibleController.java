package com.penmate.backend.interfaces.api.storybible;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.storybible.StoryBibleApplicationService;
import com.penmate.backend.application.storybible.StoryBibleUndoService;
import com.penmate.backend.application.storybible.command.StoryBibleCommands;
import com.penmate.backend.domain.storybible.model.StoryBibleActorType;
import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.storybible.dto.StoryBibleDtos;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/novels/{projectId}/story-bible")
public class StoryBibleController {

    private final StoryBibleApplicationService service;
    private final ObjectMapper objectMapper;
    private final StoryBibleUndoService undoService;

    public StoryBibleController(StoryBibleApplicationService service, ObjectMapper objectMapper,
                                StoryBibleUndoService undoService) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.undoService = undoService;
    }

    @GetMapping
    public ApiResponse<Object> get(@PathVariable String projectId, @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.get(id(projectId, "projectId")), traceId);
    }

    @PostMapping
    public ApiResponse<Object> bootstrap(@PathVariable String projectId, @RequestBody StoryBibleDtos.Bootstrap dto,
                                         Authentication authentication,
                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.bootstrap(id(projectId, "projectId"), dto.projectTitle(), actor(authentication)), traceId);
    }

    @GetMapping("/views")
    public ApiResponse<Object> listViews(@PathVariable String projectId, @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.listViewPreferences(id(projectId, "projectId")), traceId);
    }

    @PatchMapping("/views/{viewCode}")
    public ApiResponse<Object> updateView(@PathVariable String projectId, @PathVariable String viewCode,
                                          @RequestBody StoryBibleDtos.UpdateView dto, Authentication authentication,
                                          @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.updateViewPreference(id(projectId, "projectId"),
                new StoryBibleCommands.UpdateViewPreference(viewCode, dto.displayName(), dto.hidden(), dto.sortOrder()),
                actor(authentication)), traceId);
    }

    @GetMapping("/node-types")
    public ApiResponse<Object> listNodeTypes(@PathVariable String projectId, @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.listNodeTypes(id(projectId, "projectId")), traceId);
    }

    @PostMapping("/node-types")
    public ApiResponse<Object> createNodeType(@PathVariable String projectId, @RequestBody StoryBibleDtos.CreateNodeType dto,
                                              Authentication authentication,
                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.createNodeType(id(projectId, "projectId"), new StoryBibleCommands.CreateNodeType(
                dto.typeCode(), dto.semanticFamily(), dto.displayName(), dto.iconCode(), dto.fieldSchemaJson(), dto.sortOrder()
        ), actor(authentication)), traceId);
    }

    @PatchMapping("/node-types/{typeId}")
    public ApiResponse<Object> updateNodeType(@PathVariable String projectId, @PathVariable String typeId,
                                              @RequestBody StoryBibleDtos.UpdateNodeType dto, Authentication authentication,
                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.updateNodeType(id(projectId, "projectId"), id(typeId, "typeId"),
                new StoryBibleCommands.UpdateNodeType(dto.displayName(), dto.iconCode(), dto.fieldSchemaJson(), dto.sortOrder()),
                actor(authentication)), traceId);
    }

    @DeleteMapping("/node-types/{typeId}")
    public ApiResponse<String> archiveNodeType(@PathVariable String projectId, @PathVariable String typeId,
                                               Authentication authentication,
                                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        service.archiveNodeType(id(projectId, "projectId"), id(typeId, "typeId"), actor(authentication));
        return ApiResponse.success("archived", traceId);
    }

    @GetMapping("/nodes")
    public ApiResponse<Object> listNodes(@PathVariable String projectId,
                                         @RequestParam(required = false) String typeId,
                                         @RequestParam(required = false) StoryBibleCanonStatus status,
                                         @RequestParam(required = false) String query,
                                         @RequestParam(required = false) String categoryId,
                                         @RequestParam(required = false) String tagId,
                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.searchNodes(id(projectId, "projectId"), optionalId(typeId, "typeId"), status, query,
                optionalId(categoryId, "categoryId"), optionalId(tagId, "tagId"), 500), traceId);
    }

    @PostMapping("/search")
    public ApiResponse<Object> searchNodes(@PathVariable String projectId, @RequestBody StoryBibleDtos.SearchNodes dto,
                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.searchNodes(id(projectId, "projectId"), optionalId(dto.typeId(), "typeId"),
                dto.status(), dto.query(), optionalId(dto.categoryId(), "categoryId"),
                optionalId(dto.tagId(), "tagId"), dto.limit() == null ? 100 : dto.limit()), traceId);
    }

    @PostMapping("/nodes")
    public ApiResponse<Object> createNode(@PathVariable String projectId, @RequestBody StoryBibleDtos.CreateNode dto,
                                          Authentication authentication,
                                          @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.createNode(id(projectId, "projectId"), new StoryBibleCommands.CreateNode(
                id(dto.typeId(), "typeId"), dto.title(), dto.summary(), dto.bodyMarkdown(), dto.attributesJson(),
                dto.inclusionPolicy(), dto.canonStatus(), dto.aliases(), ids(dto.categoryIds(), "categoryIds"), ids(dto.tagIds(), "tagIds")
        ), StoryBibleActorType.USER, actor(authentication), null), traceId);
    }

    @GetMapping("/nodes/{nodeId}")
    public ApiResponse<Object> getNode(@PathVariable String projectId, @PathVariable String nodeId,
                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.getNodeDetails(id(projectId, "projectId"), id(nodeId, "nodeId")), traceId);
    }

    @PatchMapping("/nodes/{nodeId}")
    public ApiResponse<Object> updateNode(@PathVariable String projectId, @PathVariable String nodeId,
                                          @RequestBody StoryBibleDtos.UpdateNode dto, Authentication authentication,
                                          @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.updateNode(id(projectId, "projectId"), id(nodeId, "nodeId"), new StoryBibleCommands.UpdateNode(
                dto.expectedRevision(), id(dto.typeId(), "typeId"), dto.title(), dto.summary(), dto.bodyMarkdown(), dto.attributesJson(),
                dto.inclusionPolicy(), dto.canonStatus(), dto.aliases(), ids(dto.categoryIds(), "categoryIds"), ids(dto.tagIds(), "tagIds")
        ), StoryBibleActorType.USER, actor(authentication), null), traceId);
    }

    @DeleteMapping("/nodes/{nodeId}")
    public ApiResponse<String> deleteNode(@PathVariable String projectId, @PathVariable String nodeId,
                                          @RequestParam Long expectedRevision, Authentication authentication,
                                          @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        service.deleteNode(id(projectId, "projectId"), id(nodeId, "nodeId"), expectedRevision,
                StoryBibleActorType.USER, actor(authentication), null);
        return ApiResponse.success("deleted", traceId);
    }

    @GetMapping("/nodes/{nodeId}/effective-state")
    public ApiResponse<Object> effectiveState(@PathVariable String projectId, @PathVariable String nodeId,
                                              @RequestParam String chapterId,
                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.getEffectiveState(id(projectId, "projectId"), id(nodeId, "nodeId"), id(chapterId, "chapterId")), traceId);
    }

    @GetMapping("/categories")
    public ApiResponse<Object> listCategories(@PathVariable String projectId, @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.listCategories(id(projectId, "projectId")), traceId);
    }

    @PostMapping("/categories")
    public ApiResponse<Object> createCategory(@PathVariable String projectId, @RequestBody StoryBibleDtos.CreateCategory dto,
                                              Authentication authentication,
                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.createCategory(id(projectId, "projectId"), new StoryBibleCommands.CreateCategory(
                optionalId(dto.parentCategoryId(), "parentCategoryId"), dto.name(), dto.sortOrder()), actor(authentication)), traceId);
    }

    @PatchMapping("/categories/{categoryId}")
    public ApiResponse<Object> updateCategory(@PathVariable String projectId, @PathVariable String categoryId,
                                              @RequestBody StoryBibleDtos.UpdateCategory dto, Authentication authentication,
                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.updateCategory(id(projectId, "projectId"), id(categoryId, "categoryId"),
                new StoryBibleCommands.UpdateCategory(optionalId(dto.parentCategoryId(), "parentCategoryId"), dto.name(), dto.sortOrder()),
                actor(authentication)), traceId);
    }

    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<String> deleteCategory(@PathVariable String projectId, @PathVariable String categoryId,
                                              Authentication authentication,
                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        service.deleteCategory(id(projectId, "projectId"), id(categoryId, "categoryId"), actor(authentication));
        return ApiResponse.success("deleted", traceId);
    }

    @GetMapping("/tags")
    public ApiResponse<Object> listTags(@PathVariable String projectId, @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.listTags(id(projectId, "projectId")), traceId);
    }

    @PostMapping("/tags")
    public ApiResponse<Object> createTag(@PathVariable String projectId, @RequestBody StoryBibleDtos.CreateTag dto,
                                         Authentication authentication,
                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.createTag(id(projectId, "projectId"), new StoryBibleCommands.CreateTag(dto.name(), dto.color()),
                actor(authentication)), traceId);
    }

    @PatchMapping("/tags/{tagId}")
    public ApiResponse<Object> updateTag(@PathVariable String projectId, @PathVariable String tagId,
                                         @RequestBody StoryBibleDtos.UpdateTag dto, Authentication authentication,
                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.updateTag(id(projectId, "projectId"), id(tagId, "tagId"),
                new StoryBibleCommands.UpdateTag(dto.name(), dto.color()), actor(authentication)), traceId);
    }

    @DeleteMapping("/tags/{tagId}")
    public ApiResponse<String> deleteTag(@PathVariable String projectId, @PathVariable String tagId,
                                         Authentication authentication,
                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        service.deleteTag(id(projectId, "projectId"), id(tagId, "tagId"), actor(authentication));
        return ApiResponse.success("deleted", traceId);
    }

    @GetMapping("/relations")
    public ApiResponse<Object> listRelations(@PathVariable String projectId, @RequestParam(required = false) List<String> nodeIds,
                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.listRelations(id(projectId, "projectId"), ids(nodeIds, "nodeIds")), traceId);
    }

    @PostMapping("/relations")
    public ApiResponse<Object> createRelation(@PathVariable String projectId, @RequestBody StoryBibleDtos.CreateRelation dto,
                                              Authentication authentication,
                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.createRelation(id(projectId, "projectId"), new StoryBibleCommands.CreateRelation(
                id(dto.sourceNodeId(), "sourceNodeId"), dto.relationType(), id(dto.targetNodeId(), "targetNodeId"),
                dto.description(), dto.attributesJson()), StoryBibleActorType.USER, actor(authentication), null), traceId);
    }

    @PatchMapping("/relations/{relationId}")
    public ApiResponse<Object> updateRelation(@PathVariable String projectId, @PathVariable String relationId,
                                              @RequestBody StoryBibleDtos.UpdateRelation dto, Authentication authentication,
                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.updateRelation(id(projectId, "projectId"), id(relationId, "relationId"),
                new StoryBibleCommands.UpdateRelation(dto.expectedRevision(), dto.relationType(), id(dto.targetNodeId(), "targetNodeId"),
                        dto.description(), dto.attributesJson()), StoryBibleActorType.USER, actor(authentication), null), traceId);
    }

    @DeleteMapping("/relations/{relationId}")
    public ApiResponse<String> deleteRelation(@PathVariable String projectId, @PathVariable String relationId,
                                              @RequestParam Long expectedRevision, Authentication authentication,
                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        service.deleteRelation(id(projectId, "projectId"), id(relationId, "relationId"), expectedRevision,
                StoryBibleActorType.USER, actor(authentication), null);
        return ApiResponse.success("deleted", traceId);
    }

    @GetMapping("/progressions")
    public ApiResponse<Object> listProgressions(@PathVariable String projectId, @RequestParam(required = false) List<String> nodeIds,
                                                @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.listProgressions(id(projectId, "projectId"), ids(nodeIds, "nodeIds")), traceId);
    }

    @PostMapping("/nodes/{nodeId}/progressions")
    public ApiResponse<Object> createProgression(@PathVariable String projectId, @PathVariable String nodeId,
                                                 @RequestBody StoryBibleDtos.CreateProgression dto, Authentication authentication,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.createProgression(id(projectId, "projectId"), new StoryBibleCommands.CreateProgression(
                id(nodeId, "nodeId"), id(dto.anchorChapterId(), "anchorChapterId"), optionalId(dto.endChapterId(), "endChapterId"),
                optionalId(dto.storyEventNodeId(), "storyEventNodeId"), dto.patchJson(), dto.summary()),
                StoryBibleActorType.USER, actor(authentication), null), traceId);
    }

    @PatchMapping("/progressions/{progressionId}")
    public ApiResponse<Object> updateProgression(@PathVariable String projectId, @PathVariable String progressionId,
                                                 @RequestBody StoryBibleDtos.UpdateProgression dto, Authentication authentication,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.updateProgression(id(projectId, "projectId"), id(progressionId, "progressionId"),
                new StoryBibleCommands.UpdateProgression(dto.expectedRevision(), id(dto.anchorChapterId(), "anchorChapterId"),
                        optionalId(dto.endChapterId(), "endChapterId"), optionalId(dto.storyEventNodeId(), "storyEventNodeId"),
                        dto.patchJson(), dto.summary()), StoryBibleActorType.USER, actor(authentication), null), traceId);
    }

    @DeleteMapping("/progressions/{progressionId}")
    public ApiResponse<String> deleteProgression(@PathVariable String projectId, @PathVariable String progressionId,
                                                 @RequestParam Long expectedRevision, Authentication authentication,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        service.deleteProgression(id(projectId, "projectId"), id(progressionId, "progressionId"), expectedRevision,
                StoryBibleActorType.USER, actor(authentication), null);
        return ApiResponse.success("deleted", traceId);
    }

    @GetMapping("/changesets")
    public ApiResponse<Object> recentChanges(@PathVariable String projectId,
                                             @RequestParam(required = false) Long beforeRevision,
                                             @RequestParam(defaultValue = "50") int limit,
                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.changesetPage(id(projectId, "projectId"), beforeRevision, limit), traceId);
    }

    @GetMapping("/changesets/{changesetId}")
    public ApiResponse<Object> getChangeset(@PathVariable String projectId, @PathVariable String changesetId,
                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.getChangeset(id(projectId, "projectId"), id(changesetId, "changesetId")), traceId);
    }

    @PostMapping("/changesets/{changesetId}/undo")
    public ApiResponse<Object> undoChangeset(@PathVariable String projectId, @PathVariable String changesetId,
                                             Authentication authentication,
                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(undoService.undo(id(projectId, "projectId"), id(changesetId, "changesetId"),
                actor(authentication)), traceId);
    }

    @PostMapping("/runs/{sourceRunId}/undo")
    public ApiResponse<Object> undoRun(@PathVariable String projectId, @PathVariable String sourceRunId,
                                       Authentication authentication,
                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(undoService.undoRun(id(projectId, "projectId"), id(sourceRunId, "sourceRunId"),
                actor(authentication)), traceId);
    }

    @GetMapping("/nodes/{nodeId}/changesets")
    public ApiResponse<Object> nodeChanges(@PathVariable String projectId, @PathVariable String nodeId,
                                           @RequestParam(defaultValue = "50") int limit,
                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return success(service.nodeChanges(id(projectId, "projectId"), id(nodeId, "nodeId"), limit), traceId);
    }

    private ApiResponse<Object> success(Object value, String traceId) {
        return ApiResponse.success(normalizeIds(value), traceId);
    }

    private Object normalizeIds(Object value) {
        Object converted = objectMapper.convertValue(value, Object.class);
        return normalize(converted, null);
    }

    private Object normalize(Object value, String fieldName) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if ("id".equals(key)) continue;
                result.put(key, normalize(entry.getValue(), key));
            }
            return result;
        }
        if (value instanceof List<?> source) {
            List<Object> result = new ArrayList<>(source.size());
            for (Object item : source) result.add(normalize(item, fieldName));
            return result;
        }
        if (value instanceof Number && fieldName != null && fieldName.toLowerCase(Locale.ROOT).endsWith("id")) {
            return value.toString();
        }
        if (value instanceof Number && fieldName != null && fieldName.toLowerCase(Locale.ROOT).endsWith("ids")) {
            return value.toString();
        }
        return value;
    }

    private Long id(String raw, String field) {
        Long value = optionalId(raw, field);
        if (value == null) throw BusinessException.badRequest(field + " is required and must be a string ID");
        return value;
    }

    private Long actor(Authentication authentication) {
        return com.penmate.backend.interfaces.api.common.AuthenticatedActor.id(authentication);
    }

    private Long optionalId(String raw, String field) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            throw BusinessException.badRequest(field + " must be a string ID");
        }
    }

    private List<Long> ids(List<String> raw, String field) {
        if (raw == null || raw.isEmpty()) return List.of();
        return raw.stream().map(value -> id(value, field)).toList();
    }
}
