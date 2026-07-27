package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.agent.model.AgentQueuedRequest;
import com.penmate.backend.domain.agent.repository.AgentQueuedRequestRepository;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AgentQueuedRequestApplicationService {
    public static final String OPEN_REQUEST_MESSAGE = "请先撤回当前待执行请求";

    private final AgentQueuedRequestRepository requests;
    private final AgentSessionRepository sessions;
    private final BusinessIdGenerator ids;
    private final JsonCodec json;

    public AgentQueuedRequestApplicationService(AgentQueuedRequestRepository requests,
                                                AgentSessionRepository sessions,
                                                BusinessIdGenerator ids,
                                                JsonCodec json) {
        this.requests = requests;
        this.sessions = sessions;
        this.ids = ids;
        this.json = json;
    }

    public AgentQueuedRequest get(Long projectId, Long sessionId, Long ownerUserId) {
        requireOwner(projectId, sessionId, ownerUserId);
        return requests.findOpen(projectId, sessionId);
    }

    @Transactional
    public AgentQueuedRequest register(Long projectId, Long sessionId, Long ownerUserId,
                                       String type, QueueMessagePayload payload) {
        requireOwner(projectId, sessionId, ownerUserId);
        String normalizedType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!"MESSAGE".equals(normalizedType) && !"COMPRESS".equals(normalizedType)) {
            throw BusinessException.badRequest("待执行请求类型无效");
        }
        if ("MESSAGE".equals(normalizedType)
                && (payload == null || payload.userMessage() == null || payload.userMessage().isBlank())) {
            throw BusinessException.badRequest("消息不能为空");
        }
        if (requests.findOpen(projectId, sessionId) != null) throw BusinessException.conflict(OPEN_REQUEST_MESSAGE);
        AgentQueuedRequest request = new AgentQueuedRequest(ids.nextId(), projectId, sessionId, ownerUserId,
                normalizedType, payload == null ? null : json.write(payload), "PENDING", 0, null,
                Instant.now(), Instant.now());
        if (requests.insert(request) != 1) {
            throw BusinessException.conflict(OPEN_REQUEST_MESSAGE);
        }
        return request;
    }

    @Transactional
    public void withdraw(Long projectId, Long sessionId, Long requestId, Long ownerUserId) {
        requireOwner(projectId, sessionId, ownerUserId);
        if (requests.withdraw(projectId, sessionId, requestId, ownerUserId) != 1) {
            throw BusinessException.conflict("待执行请求已开始或不存在");
        }
    }

    private void requireOwner(Long projectId, Long sessionId, Long ownerUserId) {
        var session = sessions.findSession(projectId, sessionId);
        if (session == null) throw BusinessException.notFound("Agent session not found");
        if (!ownerUserId.equals(session.getOwnerUserId())) throw BusinessException.forbidden("Agent session belongs to another user");
    }

    public record QueueMessagePayload(String userMessage, List<String> activeSkills, TaskRequest taskRequest) {
        public QueueMessagePayload { activeSkills = activeSkills == null ? null : List.copyOf(activeSkills); }
    }

    public record TaskRequest(Long chapterId, List<Long> chapterIds, Long modelConfigId, String selectedText) {
        public TaskRequest {
            chapterIds = chapterIds == null ? List.of() : chapterIds.stream()
                    .filter(java.util.Objects::nonNull).filter(id -> id > 0).distinct().toList();
            chapterId = chapterIds.isEmpty() ? null : chapterIds.getFirst();
        }
    }
}
