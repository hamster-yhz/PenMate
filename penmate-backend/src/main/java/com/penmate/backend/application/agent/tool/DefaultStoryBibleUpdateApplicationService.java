package com.penmate.backend.application.agent.tool;

import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.storybible.StoryBibleApplicationService;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.storybible.model.StoryBibleEntry;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 故事圣经更新工具应用服务默认实现。
 */
@Service
public class DefaultStoryBibleUpdateApplicationService implements StoryBibleUpdateApplicationService {

    private final StoryBibleApplicationService storyBibleApplicationService;
    private final AgentRepository agentRepository;

    public DefaultStoryBibleUpdateApplicationService(StoryBibleApplicationService storyBibleApplicationService) {
        this(storyBibleApplicationService, null);
    }

    @Autowired
    public DefaultStoryBibleUpdateApplicationService(StoryBibleApplicationService storyBibleApplicationService,
                                                     AgentRepository agentRepository) {
        this.storyBibleApplicationService = storyBibleApplicationService;
        this.agentRepository = agentRepository;
    }

    @Override
    public ToolCallResult execute(ToolCallRequest request) {
        assertTaskOwnership(request);
        JSONObject args = AgentJsonCodec.parseObj(request == null ? null : request.toolArgsJson());
        String operation = AgentJsonCodec.getString(args, "operation").trim();
        if ("list".equalsIgnoreCase(operation)) {
            Long chapterId = args.getLong("chapterId");
            List<StoryBibleEntry> entries = storyBibleApplicationService.listEntriesForChapter(request.projectId(), chapterId);
            return ToolCallResult.success(AgentJsonCodec.toJson(entries.stream().map(this::toView).toList()));
        }
        if ("create".equalsIgnoreCase(operation)) {
            StoryBibleEntry candidate = new StoryBibleEntry();
            candidate.setProjectId(request.projectId());
            candidate.setEntryKey(AgentJsonCodec.getString(args, "entryKey").trim());
            candidate.setEntryType(AgentJsonCodec.getString(args, "entryType").trim());
            candidate.setTitle(AgentJsonCodec.getString(args, "title").trim());
            candidate.setContent(AgentJsonCodec.getString(args, "content").trim());
            candidate.setCanonicalStatus(AgentJsonCodec.getString(args, "canonicalStatus").trim());
            candidate.setRiskLevel(args.getInt("riskLevel", 1));
            candidate.setValidFromChapterId(args.getLong("chapterId"));
            StoryBibleEntry created = storyBibleApplicationService.createEntry(
                    request.projectId(),
                    candidate,
                    request.operatorId(),
                    request.traceId()
            );
            return ToolCallResult.success(AgentJsonCodec.toJson(toView(created)));
        }
        if ("update".equalsIgnoreCase(operation)) {
            StoryBibleEntry candidate = new StoryBibleEntry();
            candidate.setProjectId(request.projectId());
            candidate.setEntryKey(AgentJsonCodec.getString(args, "entryKey").trim());
            candidate.setEntryType(AgentJsonCodec.getString(args, "entryType").trim());
            candidate.setTitle(AgentJsonCodec.getString(args, "title").trim());
            candidate.setContent(AgentJsonCodec.getString(args, "content").trim());
            candidate.setCanonicalStatus(AgentJsonCodec.getString(args, "canonicalStatus").trim());
            candidate.setRiskLevel(args.getInt("riskLevel", 1));
            candidate.setValidFromChapterId(args.getLong("chapterId"));
            StoryBibleEntry updated = storyBibleApplicationService.updateEntry(
                    request.projectId(),
                    args.getLong("entryId"),
                    candidate,
                    request.operatorId(),
                    request.traceId()
            );
            return ToolCallResult.success(AgentJsonCodec.toJson(toView(updated)));
        }
        if ("delete".equalsIgnoreCase(operation)) {
            Long entryId = args.getLong("entryId");
            storyBibleApplicationService.deleteEntry(
                    request.projectId(),
                    entryId,
                    request.operatorId(),
                    request.traceId()
            );
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("operation", "delete");
            output.put("entryId", entryId);
            output.put("deleted", true);
            return ToolCallResult.success(AgentJsonCodec.toJson(output));
        }
        return new ToolCallResult("FAILED", null, null, "STORY_BIBLE_UPDATE_UNSUPPORTED", "Unsupported operation: " + operation);
    }

    private void assertTaskOwnership(ToolCallRequest request) {
        if (request == null || request.projectId() == null || request.taskId() == null || request.operatorId() == null) {
            throw new IllegalStateException("task context is required for story bible updates");
        }
        if (agentRepository == null) {
            throw new IllegalStateException("agent repository is required for story bible ownership validation");
        }
        AgentGenerationTask task = agentRepository.findGenerationTask(request.projectId(), request.taskId());
        if (task == null) {
            throw new IllegalStateException("generation task not found");
        }
        if (task.getUserId() == null) {
            throw new IllegalStateException("generation task userId is required");
        }
        if (!request.operatorId().equals(task.getUserId())) {
            throw new IllegalStateException("operator does not match generation task user");
        }
    }

    private Map<String, Object> toView(StoryBibleEntry entry) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entryId", entry == null ? null : entry.getEntryId());
        data.put("projectId", entry == null ? null : entry.getProjectId());
        data.put("entryKey", entry == null ? null : entry.getEntryKey());
        data.put("entryType", entry == null ? null : entry.getEntryType());
        data.put("title", entry == null ? null : entry.getTitle());
        data.put("content", entry == null ? null : entry.getContent());
        data.put("canonicalStatus", entry == null ? null : entry.getCanonicalStatus());
        data.put("riskLevel", entry == null ? null : entry.getRiskLevel());
        return data;
    }
}
