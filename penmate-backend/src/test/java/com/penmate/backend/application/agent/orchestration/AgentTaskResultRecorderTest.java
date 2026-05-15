package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.model.AgentTaskResult;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTaskResultRecorderTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private BusinessIdGenerator businessIdGenerator;

    @InjectMocks
    private AgentTaskResultRecorder agentTaskResultRecorder;

    @Test
    void UT_APP_AGENT_RECORD_ASSISTANT_RESULT_SHOULD_GENERATE_MESSAGE_ID_BEFORE_INSERT() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setConversationId(920002L);

        when(businessIdGenerator.nextId()).thenReturn(930001L, 960001L);
        when(agentRepository.nextMessageSeq(920002L)).thenReturn(2);
        when(agentRepository.insertMessage(any(AgentMessage.class))).thenReturn(1);
        when(agentRepository.insertTaskResult(any(AgentTaskResult.class))).thenReturn(1);
        when(agentRepository.updateGenerationTaskResultLink(isNull(), isNull(), eq(960001L))).thenReturn(1);
        when(agentRepository.touchConversationLastMessage(920002L)).thenReturn(1);

        agentTaskResultRecorder.recordAssistantResult(task, "生成完成");

        ArgumentCaptor<AgentMessage> captor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(agentRepository).insertMessage(captor.capture());
        AgentMessage inserted = captor.getValue();

        InOrder inOrder = inOrder(businessIdGenerator, agentRepository);
        inOrder.verify(businessIdGenerator).nextId();
        inOrder.verify(agentRepository).nextMessageSeq(920002L);
        inOrder.verify(agentRepository).insertMessage(any(AgentMessage.class));

        assertThat(inserted.getMessageId()).isEqualTo(930001L);
        assertThat(inserted.getConversationId()).isEqualTo(920002L);
        assertThat(inserted.getRole()).isEqualTo("assistant");
        assertThat(inserted.getUserMessageType()).isEqualTo("GENERATION_RESULT");
        assertThat(inserted.getContentMd()).isEqualTo("生成完成");
        assertThat(inserted.getSeqNo()).isEqualTo(2);
    }

    @Test
    void UT_APP_AGENT_RECORD_ASSISTANT_RESULT_SHOULD_PERSIST_TASK_RESULT_SNAPSHOT_AND_LINK_IT_TO_GENERATION_TASK() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setProjectId(920001L);
        task.setTaskId(940001L);
        task.setConversationId(920002L);

        when(businessIdGenerator.nextId()).thenReturn(930001L, 960001L);
        when(agentRepository.nextMessageSeq(920002L)).thenReturn(2);
        when(agentRepository.insertMessage(any(AgentMessage.class))).thenReturn(1);
        when(agentRepository.insertTaskResult(any(AgentTaskResult.class))).thenReturn(1);
        when(agentRepository.updateGenerationTaskResultLink(920001L, 940001L, 960001L)).thenReturn(1);
        when(agentRepository.touchConversationLastMessage(920002L)).thenReturn(1);

        agentTaskResultRecorder.recordAssistantResult(task, "生成完成");

        ArgumentCaptor<AgentTaskResult> resultCaptor = ArgumentCaptor.forClass(AgentTaskResult.class);
        verify(agentRepository).insertTaskResult(resultCaptor.capture());
        verify(agentRepository).updateGenerationTaskResultLink(920001L, 940001L, 960001L);
        assertThat(resultCaptor.getValue().getResultId()).isEqualTo(960001L);
        assertThat(resultCaptor.getValue().getTaskId()).isEqualTo(940001L);
        assertThat(resultCaptor.getValue().getOutputMarkdown()).isEqualTo("生成完成");
        assertThat(resultCaptor.getValue().getAssistantMessageId()).isEqualTo(930001L);
    }

    @Test
    void UT_APP_AGENT_RECORD_ASSISTANT_RESULT_SHOULD_PERSIST_STRUCTURED_OUTPUT_AND_TOOL_TRACE_WHEN_PROVIDED() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setProjectId(920001L);
        task.setTaskId(940001L);
        task.setConversationId(920002L);
        String structuredJson = "{\"draftText\":\"第三章初稿正文\",\"operation\":\"generate\",\"preservedConstraints\":[\"保留第一人称\"],\"sourceSummary\":\"第三章提纲\"}";

        when(businessIdGenerator.nextId()).thenReturn(930001L, 960001L);
        when(agentRepository.nextMessageSeq(920002L)).thenReturn(2);
        when(agentRepository.insertMessage(any(AgentMessage.class))).thenReturn(1);
        when(agentRepository.insertTaskResult(any(AgentTaskResult.class))).thenReturn(1);
        when(agentRepository.updateGenerationTaskResultLink(920001L, 940001L, 960001L)).thenReturn(1);
        when(agentRepository.touchConversationLastMessage(920002L)).thenReturn(1);

        agentTaskResultRecorder.recordAssistantResult(task, "生成完成", structuredJson);

        ArgumentCaptor<AgentTaskResult> resultCaptor = ArgumentCaptor.forClass(AgentTaskResult.class);
        verify(agentRepository).insertTaskResult(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getOutputMarkdown()).isEqualTo("生成完成");
        assertThat(resultCaptor.getValue().getOutputStructuredJson()).isEqualTo(structuredJson);
        assertThat(resultCaptor.getValue().getToolTraceJson()).isEqualTo(structuredJson);
        assertThat(resultCaptor.getValue().getDraftSummary()).contains("\"draftText\":\"第三章初稿正文\"");
        assertThat(resultCaptor.getValue().getQualityReportSummary()).isNull();
        assertThat(resultCaptor.getValue().getTodoSummary()).isNull();
        assertThat(resultCaptor.getValue().getStoryBibleProposalSummary()).isNull();
    }

    @Test
    void UT_APP_AGENT_RECORD_ASSISTANT_RESULT_SHOULD_PERSIST_QUALITY_REVIEW_STRUCTURED_OUTPUT_AND_TOOL_TRACE_WHEN_PROVIDED() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setProjectId(920001L);
        task.setTaskId(940001L);
        task.setConversationId(920002L);
        String structuredJson = "{\"score\":61,\"passes\":[\"用户要求主冲突已出现\"],\"issues\":[{\"dimension\":\"PLOT_LOGIC\",\"severity\":\"HIGH\",\"summary\":\"主角提前知道密令\",\"evidence\":\"第二段直接复述密令\",\"suggestion\":\"改为侍从转述\"}],\"needsRevision\":true,\"riskFlags\":[\"PLOT_HOLE\"],\"revisionSuggestions\":[{\"priority\":\"P0\",\"target\":\"剧情逻辑\",\"instruction\":\"修复密令来源\",\"rationale\":\"避免剧情漏洞\"}],\"currentRevisionRound\":1,\"maxRevisionRounds\":2,\"revisionAllowed\":true,\"reviewSummary\":\"存在剧情逻辑问题，需要修订。\"}";

        when(businessIdGenerator.nextId()).thenReturn(930001L, 960001L);
        when(agentRepository.nextMessageSeq(920002L)).thenReturn(2);
        when(agentRepository.insertMessage(any(AgentMessage.class))).thenReturn(1);
        when(agentRepository.insertTaskResult(any(AgentTaskResult.class))).thenReturn(1);
        when(agentRepository.updateGenerationTaskResultLink(920001L, 940001L, 960001L)).thenReturn(1);
        when(agentRepository.touchConversationLastMessage(920002L)).thenReturn(1);

        agentTaskResultRecorder.recordAssistantResult(task, "审查完成", structuredJson);

        ArgumentCaptor<AgentTaskResult> resultCaptor = ArgumentCaptor.forClass(AgentTaskResult.class);
        verify(agentRepository).insertTaskResult(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getOutputMarkdown()).isEqualTo("审查完成");
        assertThat(resultCaptor.getValue().getOutputStructuredJson()).isEqualTo(structuredJson);
        assertThat(resultCaptor.getValue().getToolTraceJson()).isEqualTo(structuredJson);
        assertThat(resultCaptor.getValue().getDraftSummary()).isNull();
        assertThat(resultCaptor.getValue().getQualityReportSummary()).contains("\"reviewSummary\":\"存在剧情逻辑问题，需要修订。\"");
    }

    @Test
    void UT_APP_AGENT_RECORD_ASSISTANT_RESULT_SHOULD_PERSIST_LAST_STRUCTURED_TOOL_OUTPUT_WHEN_TOOL_TRACE_CONTAINS_MULTIPLE_TOOL_RESULTS() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setProjectId(920001L);
        task.setTaskId(940001L);
        task.setConversationId(920002L);
        String draftStructuredJson = "{\"draftText\":\"第三章初稿正文\",\"operation\":\"generate\",\"preservedConstraints\":[\"保留第一人称\"],\"sourceSummary\":\"第三章提纲\"}";
        String qualityStructuredJson = "{\"score\":61,\"passes\":[\"用户要求主冲突已出现\"],\"issues\":[{\"dimension\":\"PLOT_LOGIC\",\"severity\":\"HIGH\",\"summary\":\"主角提前知道密令\",\"evidence\":\"第二段直接复述密令\",\"suggestion\":\"改为侍从转述\"}],\"needsRevision\":true,\"riskFlags\":[\"PLOT_HOLE\"],\"revisionSuggestions\":[{\"priority\":\"P0\",\"target\":\"剧情逻辑\",\"instruction\":\"修复密令来源\",\"rationale\":\"避免剧情漏洞\"}],\"currentRevisionRound\":1,\"maxRevisionRounds\":2,\"revisionAllowed\":true,\"reviewSummary\":\"存在剧情逻辑问题，需要修订。\"}";
        String aggregatedToolTraceJson = draftStructuredJson + "\n" + qualityStructuredJson;

        when(businessIdGenerator.nextId()).thenReturn(930001L, 960001L);
        when(agentRepository.nextMessageSeq(920002L)).thenReturn(2);
        when(agentRepository.insertMessage(any(AgentMessage.class))).thenReturn(1);
        when(agentRepository.insertTaskResult(any(AgentTaskResult.class))).thenReturn(1);
        when(agentRepository.updateGenerationTaskResultLink(920001L, 940001L, 960001L)).thenReturn(1);
        when(agentRepository.touchConversationLastMessage(920002L)).thenReturn(1);

        agentTaskResultRecorder.recordAssistantResult(task, "最终答复", aggregatedToolTraceJson);

        ArgumentCaptor<AgentTaskResult> resultCaptor = ArgumentCaptor.forClass(AgentTaskResult.class);
        verify(agentRepository).insertTaskResult(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getOutputMarkdown()).isEqualTo("最终答复");
        assertThat(resultCaptor.getValue().getOutputStructuredJson()).isEqualTo(qualityStructuredJson);
        assertThat(resultCaptor.getValue().getToolTraceJson()).isEqualTo(aggregatedToolTraceJson);
        assertThat(resultCaptor.getValue().getDraftSummary()).contains("\"draftText\":\"第三章初稿正文\"");
        assertThat(resultCaptor.getValue().getQualityReportSummary()).contains("\"reviewSummary\":\"存在剧情逻辑问题，需要修订。\"");
    }

    @Test
    void UT_APP_AGENT_RECORD_ASSISTANT_RESULT_SHOULD_PERSIST_TODO_AND_STORY_BIBLE_SUMMARIES_WHEN_TOOL_TRACE_CONTAINS_MULTI_TOOL_RESULTS() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setProjectId(920001L);
        task.setTaskId(940001L);
        task.setConversationId(920002L);
        String todoStructuredJson = "{\"planTitle\":\"第三章修订待办\",\"planSummary\":\"先修复逻辑漏洞再补人物动作\",\"recommendedNextAction\":\"创建待办并进入第一项\",\"items\":[{\"title\":\"修复密令来源\",\"description\":\"补充侍从转述桥段\",\"priority\":\"P0\",\"sourceType\":\"QUALITY_REVIEW\",\"recommendedStatus\":\"TODO\",\"suggestedAutoCreate\":true,\"rationale\":\"避免剧情漏洞\",\"acceptanceCriteria\":[\"密令来源明确\"]}]}";
        String storyBibleProposalJson = "{\"proposalSummary\":\"建议补充侍从知晓密令的设定\",\"items\":[{\"entryKey\":\"maid.secret_order\",\"entryType\":\"CHARACTER_KNOWLEDGE\",\"proposedContent\":\"侍从知晓密令并负责转述\",\"canonicalStatus\":\"PROPOSED\",\"riskLevel\":2,\"sourceText\":\"第二段侍从转述密令\",\"sourceChapterId\":301,\"inferenceLevel\":\"DIRECT\"}]}";
        String aggregatedToolTraceJson = todoStructuredJson + "\n" + storyBibleProposalJson;

        when(businessIdGenerator.nextId()).thenReturn(930001L, 960001L);
        when(agentRepository.nextMessageSeq(920002L)).thenReturn(2);
        when(agentRepository.insertMessage(any(AgentMessage.class))).thenReturn(1);
        when(agentRepository.insertTaskResult(any(AgentTaskResult.class))).thenReturn(1);
        when(agentRepository.updateGenerationTaskResultLink(920001L, 940001L, 960001L)).thenReturn(1);
        when(agentRepository.touchConversationLastMessage(920002L)).thenReturn(1);

        agentTaskResultRecorder.recordAssistantResult(task, "规划与设定建议完成", aggregatedToolTraceJson);

        ArgumentCaptor<AgentTaskResult> resultCaptor = ArgumentCaptor.forClass(AgentTaskResult.class);
        verify(agentRepository).insertTaskResult(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getTodoSummary()).contains("\"planTitle\":\"第三章修订待办\"");
        assertThat(resultCaptor.getValue().getStoryBibleProposalSummary()).contains("\"proposalSummary\":\"建议补充侍从知晓密令的设定\"");
    }

    @Test
    void UT_APP_AGENT_RECORD_ASSISTANT_RESULT_SHOULD_PERSIST_STORY_BIBLE_APPROVAL_SUMMARY_WHEN_TOOL_TRACE_CONTAINS_APPROVAL_VIEW() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setProjectId(920001L);
        task.setTaskId(940001L);
        task.setConversationId(920002L);
        String storyBibleApprovalJson = "{\"approvalId\":88001,\"approvalType\":\"STORY_BIBLE_UPDATE\",\"proposalSummary\":\"建议补充侍从知晓密令的设定\",\"entryKeys\":[\"maid.secret_order\"],\"nextAction\":\"await_approval\"}";

        when(businessIdGenerator.nextId()).thenReturn(930001L, 960001L);
        when(agentRepository.nextMessageSeq(920002L)).thenReturn(2);
        when(agentRepository.insertMessage(any(AgentMessage.class))).thenReturn(1);
        when(agentRepository.insertTaskResult(any(AgentTaskResult.class))).thenReturn(1);
        when(agentRepository.updateGenerationTaskResultLink(920001L, 940001L, 960001L)).thenReturn(1);
        when(agentRepository.touchConversationLastMessage(920002L)).thenReturn(1);

        agentTaskResultRecorder.recordAssistantResult(task, "故事圣经待确认", storyBibleApprovalJson);

        ArgumentCaptor<AgentTaskResult> resultCaptor = ArgumentCaptor.forClass(AgentTaskResult.class);
        verify(agentRepository).insertTaskResult(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getStoryBibleProposalSummary()).contains("\"approvalType\":\"STORY_BIBLE_UPDATE\"");
    }

    @Test
    void UT_APP_AGENT_RECORD_ASSISTANT_RESULT_SHOULD_PREFER_STORY_BIBLE_PROPOSAL_FRAGMENT_WHEN_TRACE_CONTAINS_PROPOSAL_THEN_APPROVAL() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setProjectId(920001L);
        task.setTaskId(940001L);
        task.setConversationId(920002L);
        String proposalJson = "{\"proposalSummary\":\"建议补充侍从知晓密令的设定\",\"items\":[{\"entryKey\":\"maid.secret_order\",\"entryType\":\"CHARACTER_KNOWLEDGE\",\"proposedContent\":\"侍从知晓密令并负责转述\",\"canonicalStatus\":\"PROPOSED\",\"riskLevel\":2,\"sourceText\":\"第二段侍从转述密令\",\"sourceChapterId\":301,\"inferenceLevel\":\"DIRECT\"}]}";
        String approvalJson = "{\"approvalType\":\"STORY_BIBLE_UPDATE\",\"proposalSummary\":\"故事圣经更新待确认\",\"entryKeys\":[\"maid.secret_order\"],\"nextAction\":\"await_approval\"}";
        String aggregatedToolTraceJson = proposalJson + "\n" + approvalJson;

        when(businessIdGenerator.nextId()).thenReturn(930001L, 960001L);
        when(agentRepository.nextMessageSeq(920002L)).thenReturn(2);
        when(agentRepository.insertMessage(any(AgentMessage.class))).thenReturn(1);
        when(agentRepository.insertTaskResult(any(AgentTaskResult.class))).thenReturn(1);
        when(agentRepository.updateGenerationTaskResultLink(920001L, 940001L, 960001L)).thenReturn(1);
        when(agentRepository.touchConversationLastMessage(920002L)).thenReturn(1);

        agentTaskResultRecorder.recordAssistantResult(task, "故事圣经待确认", aggregatedToolTraceJson);

        ArgumentCaptor<AgentTaskResult> resultCaptor = ArgumentCaptor.forClass(AgentTaskResult.class);
        verify(agentRepository).insertTaskResult(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getStoryBibleProposalSummary()).contains("\"proposalSummary\":\"建议补充侍从知晓密令的设定\"");
    }

    @Test
    void UT_APP_AGENT_RECORD_ASSISTANT_RESULT_SHOULD_PREFER_STORY_BIBLE_PROPOSAL_FRAGMENT_WHEN_TRACE_CONTAINS_APPROVAL_THEN_PROPOSAL() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setProjectId(920001L);
        task.setTaskId(940001L);
        task.setConversationId(920002L);
        String approvalJson = "{\"approvalType\":\"STORY_BIBLE_UPDATE\",\"proposalSummary\":\"故事圣经更新待确认\",\"entryKeys\":[\"maid.secret_order\"],\"nextAction\":\"await_approval\"}";
        String proposalJson = "{\"proposalSummary\":\"建议补充侍从知晓密令的设定\",\"items\":[{\"entryKey\":\"maid.secret_order\",\"entryType\":\"CHARACTER_KNOWLEDGE\",\"proposedContent\":\"侍从知晓密令并负责转述\",\"canonicalStatus\":\"PROPOSED\",\"riskLevel\":2,\"sourceText\":\"第二段侍从转述密令\",\"sourceChapterId\":301,\"inferenceLevel\":\"DIRECT\"}]}";
        String aggregatedToolTraceJson = approvalJson + "\n" + proposalJson;

        when(businessIdGenerator.nextId()).thenReturn(930001L, 960001L);
        when(agentRepository.nextMessageSeq(920002L)).thenReturn(2);
        when(agentRepository.insertMessage(any(AgentMessage.class))).thenReturn(1);
        when(agentRepository.insertTaskResult(any(AgentTaskResult.class))).thenReturn(1);
        when(agentRepository.updateGenerationTaskResultLink(920001L, 940001L, 960001L)).thenReturn(1);
        when(agentRepository.touchConversationLastMessage(920002L)).thenReturn(1);

        agentTaskResultRecorder.recordAssistantResult(task, "故事圣经待确认", aggregatedToolTraceJson);

        ArgumentCaptor<AgentTaskResult> resultCaptor = ArgumentCaptor.forClass(AgentTaskResult.class);
        verify(agentRepository).insertTaskResult(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getStoryBibleProposalSummary()).contains("\"proposalSummary\":\"建议补充侍从知晓密令的设定\"");
    }

    @Test
    void UT_APP_AGENT_RECORD_ASSISTANT_RESULT_SHOULD_FAIL_WHEN_MESSAGE_INSERT_AFFECTS_ZERO_ROWS() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setProjectId(920001L);
        task.setTaskId(940001L);
        task.setConversationId(920002L);

        when(businessIdGenerator.nextId()).thenReturn(930001L);
        when(agentRepository.nextMessageSeq(920002L)).thenReturn(2);
        when(agentRepository.insertMessage(any(AgentMessage.class))).thenReturn(0);

        assertThatThrownBy(() -> agentTaskResultRecorder.recordAssistantResult(task, "生成完成"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to insert assistant result message");
    }

    @Test
    void UT_APP_AGENT_RECORD_ASSISTANT_RESULT_SHOULD_FAIL_WHEN_RESULT_LINK_UPDATE_AFFECTS_ZERO_ROWS() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setProjectId(920001L);
        task.setTaskId(940001L);
        task.setConversationId(920002L);

        when(businessIdGenerator.nextId()).thenReturn(930001L, 960001L);
        when(agentRepository.nextMessageSeq(920002L)).thenReturn(2);
        when(agentRepository.insertMessage(any(AgentMessage.class))).thenReturn(1);
        when(agentRepository.insertTaskResult(any(AgentTaskResult.class))).thenReturn(1);
        when(agentRepository.updateGenerationTaskResultLink(920001L, 940001L, 960001L)).thenReturn(0);

        assertThatThrownBy(() -> agentTaskResultRecorder.recordAssistantResult(task, "生成完成"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to link generation task result");
    }

    @Test
    void UT_APP_AGENT_RECORD_ASSISTANT_RESULT_SHOULD_FAIL_WHEN_TOUCH_LAST_MESSAGE_AFFECTS_ZERO_ROWS() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setProjectId(920001L);
        task.setTaskId(940001L);
        task.setConversationId(920002L);

        when(businessIdGenerator.nextId()).thenReturn(930001L, 960001L);
        when(agentRepository.nextMessageSeq(920002L)).thenReturn(2);
        when(agentRepository.insertMessage(any(AgentMessage.class))).thenReturn(1);
        when(agentRepository.insertTaskResult(any(AgentTaskResult.class))).thenReturn(1);
        when(agentRepository.updateGenerationTaskResultLink(920001L, 940001L, 960001L)).thenReturn(1);
        when(agentRepository.touchConversationLastMessage(920002L)).thenReturn(0);

        assertThatThrownBy(() -> agentTaskResultRecorder.recordAssistantResult(task, "生成完成"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to touch conversation last message");
    }

    @Test
    void UT_APP_AGENT_RECORD_ASSISTANT_RESULT_SHOULD_FAIL_WHEN_TASK_RESULT_INSERT_AFFECTS_ZERO_ROWS() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setProjectId(920001L);
        task.setTaskId(940001L);
        task.setConversationId(920002L);

        when(businessIdGenerator.nextId()).thenReturn(930001L, 960001L);
        when(agentRepository.nextMessageSeq(920002L)).thenReturn(2);
        when(agentRepository.insertMessage(any(AgentMessage.class))).thenReturn(1);
        when(agentRepository.insertTaskResult(any(AgentTaskResult.class))).thenReturn(0);

        assertThatThrownBy(() -> agentTaskResultRecorder.recordAssistantResult(task, "生成完成"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to insert task result snapshot");
    }
}
