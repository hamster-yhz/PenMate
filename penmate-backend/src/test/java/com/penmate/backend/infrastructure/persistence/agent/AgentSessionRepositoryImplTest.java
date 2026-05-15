package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentSessionRecoverySnapshot;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSessionRepositoryImplTest {

    @Test
    void should_define_session_lock_mapper_method_for_turn_sequence_allocation() {
        assertThatCode(() -> AgentSessionMapper.class.getMethod(
                "lockSessionForTurnAppend",
                Long.class,
                Long.class
        )).doesNotThrowAnyException();
    }

    @Test
    void should_call_session_lock_before_reading_max_turn_sequence() throws Exception {
        AgentSessionMapper mapper = mock(AgentSessionMapper.class);
        BusinessIdGenerator businessIdGenerator = mock(BusinessIdGenerator.class);
        AgentSessionRepositoryImpl repository = new AgentSessionRepositoryImpl(mapper, businessIdGenerator);
        when(mapper.maxTurnSeq(90001L)).thenReturn(3);

        repository.nextTurnSeq(90001L);

        inOrder(mapper).verify(mapper).lockSessionForTurnAppend(null, 90001L);
        inOrder(mapper).verify(mapper).maxTurnSeq(90001L);
    }

    @Test
    void should_return_recovery_snapshot_when_session_exists() {
        AgentSessionMapper mapper = mock(AgentSessionMapper.class);
        BusinessIdGenerator businessIdGenerator = mock(BusinessIdGenerator.class);
        AgentSessionRepositoryImpl repository = new AgentSessionRepositoryImpl(mapper, businessIdGenerator);
        when(mapper.findSessionRow(101L, 90001L)).thenReturn(Map.of(
                "id", 1L,
                "sessionId", 90001L,
                "projectId", 101L,
                "ownerUserId", 201L,
                "title", "第三章夜雨追踪",
                "sessionStatus", "ACTIVE",
                "boundStyleId", 81L,
                "activeContextVersion", 1,
                "lastTurnId", 60001L,
                "lastTaskId", 70001L
        ));
        when(mapper.findTaskRow(90001L, 70001L)).thenReturn(Map.of(
                "taskId", 70001L,
                "taskStatus", "WAITING_APPROVAL"
        ));
        when(mapper.findTaskContextRow(70001L)).thenReturn(null);
        when(mapper.listMessageRows(90001L)).thenReturn(List.of());

        AgentSessionRecoverySnapshot snapshot = repository.findRecoverySnapshot(101L, 90001L);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getSession()).isNotNull();
        assertThat(snapshot.getSession().getSessionId()).isEqualTo(90001L);
        assertThat(snapshot.getSession().getBoundStyle()).isEqualTo(81L);
        assertThat(snapshot.getActiveTask()).isNotNull();
        assertThat(snapshot.getActiveTask().getTaskId()).isEqualTo(70001L);
        assertThat(snapshot.getActiveTask().getTaskStatus()).isEqualTo("WAITING_APPROVAL");
    }

    @Test
    void should_build_structured_runtime_and_result_summary_into_workbench_context_when_recovery_snapshot_is_loaded() {
        AgentSessionMapper mapper = mock(AgentSessionMapper.class);
        BusinessIdGenerator businessIdGenerator = mock(BusinessIdGenerator.class);
        AgentSessionRepositoryImpl repository = new AgentSessionRepositoryImpl(mapper, businessIdGenerator);
        when(mapper.findSessionRow(101L, 90001L)).thenReturn(Map.of(
                "id", 1L,
                "sessionId", 90001L,
                "projectId", 101L,
                "ownerUserId", 201L,
                "title", "第三章夜雨追踪",
                "sessionStatus", "ACTIVE",
                "boundStyleId", 81L,
                "activeContextVersion", 1,
                "lastTurnId", 60001L,
                "lastTaskId", 70001L
        ));
        when(mapper.findTaskRow(90001L, 70001L)).thenReturn(Map.of(
                "taskId", 70001L,
                "turnId", 60001L,
                "taskStatus", "WAITING_APPROVAL",
                "activeApprovalId", 88001L
        ));
        Map<String, Object> contextRow = new LinkedHashMap<>();
        contextRow.put("contextId", 71001L);
        contextRow.put("taskId", 70001L);
        contextRow.put("chapterId", 301L);
        contextRow.put("selectedText", "夜雨中的追踪在巷口停住。");
        contextRow.put("outlineSnapshotJson", "{\"chapterTitle\":\"第三章夜雨追踪\"}");
        contextRow.put("taskProfileJson", "{\"executionProfile\":\"default\"}");
        contextRow.put("promptPlanJson", "{\"finalProfile\":\"default\"}");
        contextRow.put("contextPackageJson", "{\"chapterScope\":\"chapter:301\"}");
        contextRow.put("activeToolCallsSnapshot", "[{\"toolCode\":\"quality_review\",\"status\":\"WAITING_APPROVAL\"}]");
        contextRow.put("lastRuntimeStatus", "WAITING_APPROVAL");
        contextRow.put("recoveryCursor", "approval:88001");
        contextRow.put("contextHash", "ctx-1");
        when(mapper.findTaskContextRow(70001L)).thenReturn(contextRow);
        when(mapper.findTaskResultRow(70001L)).thenReturn(Map.of(
                "draftSummary", "{\"draftText\":\"第三章初稿正文\"}",
                "qualityReportSummary", "{\"reviewSummary\":\"存在剧情逻辑问题，需要修订。\"}",
                "todoSummary", "{\"planTitle\":\"第三章修订待办\"}",
                "storyBibleProposalSummary", "{\"proposalSummary\":\"建议补充侍从知晓密令的设定\"}"
        ));
        when(mapper.listMessageRows(90001L)).thenReturn(List.of());

        AgentSessionRecoverySnapshot snapshot = repository.findRecoverySnapshot(101L, 90001L);

        assertThat(snapshot.getWorkbenchContext()).contains("\"activeTaskRuntime\"");
        assertThat(snapshot.getWorkbenchContext()).contains("\"lastRuntimeStatus\":\"waiting_approval\"");
        assertThat(snapshot.getWorkbenchContext()).contains("\"recoveryCursor\":\"approval:88001\"");
        assertThat(snapshot.getWorkbenchContext()).contains("\"activeToolCallsSnapshot\"");
        assertThat(snapshot.getWorkbenchContext()).contains("\"status\":\"waiting_approval\"");
        assertThat(snapshot.getWorkbenchContext()).contains("\"resultSummary\"");
        assertThat(snapshot.getWorkbenchContext()).contains("\"draftSummary\":{\"draftText\":\"第三章初稿正文\"}");
        assertThat(snapshot.getWorkbenchContext()).contains("\"qualityReportSummary\":{\"reviewSummary\":\"存在剧情逻辑问题，需要修订。\"}");
        assertThat(snapshot.getWorkbenchContext()).contains("\"todoSummary\":{\"planTitle\":\"第三章修订待办\"}");
        assertThat(snapshot.getWorkbenchContext()).contains("\"storyBibleProposalSummary\":{\"proposalSummary\":\"建议补充侍从知晓密令的设定\"}");
    }
}
