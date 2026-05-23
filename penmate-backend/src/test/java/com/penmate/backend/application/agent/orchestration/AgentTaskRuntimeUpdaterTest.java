package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.context.ContextPackage;
import com.penmate.backend.application.agent.orchestration.profile.TaskIntentTag;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import com.penmate.backend.application.agent.prompt.PromptModulePlan;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTaskRuntimeUpdaterTest {

    @Mock
    private AgentRepository agentRepository;

    @InjectMocks
    private AgentTaskRuntimeUpdater agentTaskRuntimeUpdater;

    @Test
    void UT_APP_AGENT_TASK_RUNTIME_UPDATER_SHOULD_PERSIST_STRUCTURED_RUNTIME_SNAPSHOTS_WHEN_SNAPSHOT_AWARE_ENTRY_IS_USED() {
        TaskProfile taskProfile = new TaskProfile(
                List.of(TaskIntentTag.DRAFT_GENERATION),
                "default",
                List.of("writer"),
                List.of("story_bible_lookup"),
                List.of("不得违背设定"),
                "输出正文",
                false,
                true,
                false,
                "先核对设定再生成"
        );
        PromptPlan promptPlan = new PromptPlan(
                List.of(new PromptModulePlan("execution:default", "prompt-source", true, "test")),
                List.of("writer"),
                "default",
                "system prompt preview"
        );
        ContextPackage contextPackage = new ContextPackage(
                List.of("story-bible"),
                List.of("story_bible_missing"),
                List.of(),
                List.of("角色年龄：17（canon）"),
                List.of(),
                "{\"styleId\":81}",
                "chapter:3005"
        );

        AgentTaskContext taskContext = AgentTaskContext.runningOf(71001L, 33L, "RUNNING", 3005L, "夜雨中的追踪在巷口停住。");
        String activeToolCallsSnapshot = "[{\"toolCallId\":\"tool-1\",\"toolCode\":\"quality_review\",\"status\":\"RUNNING\"}]";

        when(agentRepository.updateGenerationTaskRuntime(eq(1L), eq(33L), nullable(String.class), anyString(), eq("trace-runtime")))
                .thenReturn(1);
        when(agentRepository.updateGenerationTaskSnapshots(eq(1L), eq(33L), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1);

        agentTaskRuntimeUpdater.updateGenerationRuntime(
                1L,
                33L,
                "完成后持久化运行时快照",
                "这是带快照的完成答复",
                "trace-runtime",
                taskContext,
                taskProfile,
                promptPlan,
                contextPackage,
                activeToolCallsSnapshot,
                "QUALITY_REVIEW",
                "tool_call:quality_review:tool-1"
        );

        ArgumentCaptor<String> taskProfileJsonCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> promptPlanJsonCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contextPackageJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentRepository).updateGenerationTaskSnapshots(
                eq(1L),
                eq(33L),
                taskProfileJsonCaptor.capture(),
                promptPlanJsonCaptor.capture(),
                contextPackageJsonCaptor.capture(),
                eq(activeToolCallsSnapshot),
                eq("QUALITY_REVIEW"),
                eq("tool_call:quality_review:tool-1")
        );

        assertThat(taskProfileJsonCaptor.getValue()).contains("\"executionProfile\":\"default\"");
        assertThat(taskProfileJsonCaptor.getValue()).contains("\"tools\":[\"story_bible_lookup\"]");
        assertThat(promptPlanJsonCaptor.getValue()).contains("\"finalProfile\":\"default\"");
        assertThat(promptPlanJsonCaptor.getValue()).contains("\"moduleKey\":\"execution:default\"");
        assertThat(contextPackageJsonCaptor.getValue()).contains("\"missingContextFlags\":[\"story_bible_missing\"]");
        assertThat(contextPackageJsonCaptor.getValue()).contains("\"chapterScope\":\"chapter:3005\"");
        assertThat(taskContext.getActiveToolCallsSnapshot()).isEqualTo(activeToolCallsSnapshot);
        assertThat(taskContext.getLastRuntimeStatus()).isEqualTo("QUALITY_REVIEW");
        assertThat(taskContext.getRecoveryCursor()).isEqualTo("tool_call:quality_review:tool-1");
    }

    @Test
    void UT_APP_AGENT_TASK_RUNTIME_UPDATER_SHOULD_NOT_OVERWRITE_EXISTING_TOKEN_USAGE_JSON_WHEN_UPDATING_COST() {
        when(agentRepository.updateGenerationTaskRuntime(eq(1L), eq(36L), nullable(String.class), anyString(), eq("trace-keep-token")))
                .thenReturn(1);

        agentTaskRuntimeUpdater.updateGenerationRuntime(
                1L,
                36L,
                "完成后持久化运行时快照",
                "这是带快照的完成答复",
                "trace-keep-token"
        );

        ArgumentCaptor<String> tokenUsageCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> costJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentRepository).updateGenerationTaskRuntime(
                eq(1L),
                eq(36L),
                tokenUsageCaptor.capture(),
                costJsonCaptor.capture(),
                eq("trace-keep-token")
        );
        assertThat(tokenUsageCaptor.getValue()).isNull();
        assertThat(costJsonCaptor.getValue()).contains("\"currency\":\"USD\"");
    }

    @Test
    void UT_APP_AGENT_TASK_RUNTIME_UPDATER_SHOULD_FAIL_WHEN_RUNTIME_ROW_UPDATE_AFFECTS_ZERO_ROWS() {
        when(agentRepository.updateGenerationTaskRuntime(eq(1L), eq(34L), nullable(String.class), anyString(), eq("trace-runtime-zero")))
                .thenReturn(0);

        assertThatThrownBy(() -> agentTaskRuntimeUpdater.updateGenerationRuntime(
                1L,
                34L,
                "prompt",
                "output",
                "trace-runtime-zero"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to update generation task runtime");
    }

    @Test
    void UT_APP_AGENT_TASK_RUNTIME_UPDATER_SHOULD_FAIL_WHEN_SNAPSHOT_ROW_UPDATE_AFFECTS_ZERO_ROWS() {
        TaskProfile taskProfile = new TaskProfile(
                List.of(TaskIntentTag.DRAFT_GENERATION),
                "default",
                List.of("writer"),
                List.of("story_bible_lookup"),
                List.of("不得违背设定"),
                "输出正文",
                false,
                true,
                false,
                "先核对设定再生成"
        );
        PromptPlan promptPlan = new PromptPlan(
                List.of(new PromptModulePlan("execution:default", "prompt-source", true, "test")),
                List.of("writer"),
                "default",
                "system prompt preview"
        );
        ContextPackage contextPackage = new ContextPackage(
                List.of("story-bible"),
                List.of("story_bible_missing"),
                List.of(),
                List.of("角色年龄：17（canon）"),
                List.of(),
                "{\"styleId\":81}",
                "chapter:3005"
        );

        when(agentRepository.updateGenerationTaskRuntime(eq(1L), eq(35L), nullable(String.class), anyString(), eq("trace-snapshot-zero")))
                .thenReturn(1);
        when(agentRepository.updateGenerationTaskSnapshots(eq(1L), eq(35L), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0);

        assertThatThrownBy(() -> agentTaskRuntimeUpdater.updateGenerationRuntime(
                1L,
                35L,
                "完成后持久化运行时快照",
                "这是带快照的完成答复",
                "trace-snapshot-zero",
                AgentTaskContext.runningOf(71002L, 35L, "RUNNING", 3005L, "夜雨停在巷口"),
                taskProfile,
                promptPlan,
                contextPackage,
                "[]",
                "PLANNING",
                "prompt_plan"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to update generation task snapshots");
    }
}
