package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.application.agent.prompt.SystemPromptBundle;
import com.penmate.backend.application.agent.prompt.SystemPromptDocument;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import com.penmate.backend.domain.agent.model.AgentGenerationTask;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentPromptAssemblerTest {

    @Mock
    private SystemPromptProvider systemPromptProvider;

    @InjectMocks
    private AgentPromptAssembler agentPromptAssembler;

    @Test
    void should_prepend_system_message_and_keep_user_prompt_separate_for_execution_profile_with_style_rag_and_story_bible() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setTaskType("WRITE");
        task.setPromptSnapshot("请续写主角夜访城门后的场景");

        AgentTaskContext taskContext = new AgentTaskContext();
        taskContext.setStyleSnapshotJson("{\"styleId\":81,\"tone\":\"克制\"}");

        RagRetrievedChunk ragChunk = new RagRetrievedChunk();
        ragChunk.setDocumentTitle("设定集");
        ragChunk.setChunkNo(2);
        ragChunk.setContentText("王都实行夜禁，子时后城门关闭。 ");

        when(systemPromptProvider.loadBundle("execution", "default")).thenReturn(new SystemPromptBundle(
                "execution",
                "default",
                List.of(new SystemPromptDocument(
                        "00-base-role.md",
                        "prompts/agent/system/execution/default/00-base-role.md",
                        "你是执行代理"
                )),
                "你是执行代理"
        ));

        List<Map<String, Object>> messages = agentPromptAssembler.buildExecutionMessages(
                task,
                taskContext,
                List.of(ragChunk),
                "default",
                "世界圣经摘录：角色夜行许可仅授予王室密探。"
        );

        verify(systemPromptProvider).loadBundle("execution", "default");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0))
                .containsEntry("role", "system")
                .containsEntry("content", "你是执行代理");
        assertThat(messages.get(1)).containsEntry("role", "user");
        assertThat((String) messages.get(1).get("content"))
                .contains("写作风格约束：\n{\"styleId\":81,\"tone\":\"克制\"}")
                .contains("知识库参考：\n- [设定集#2] 王都实行夜禁，子时后城门关闭。 ")
                .contains("故事圣经参考：\n世界圣经摘录：角色夜行许可仅授予王室密探。")
                .contains("用户指令：\n请续写主角夜访城门后的场景");
    }

    @Test
    void should_not_render_empty_story_bible_heading_when_story_bible_is_absent() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setTaskType("WRITE");
        task.setPromptSnapshot("只回答用户问题");

        when(systemPromptProvider.loadBundle("execution", "default")).thenReturn(new SystemPromptBundle(
                "execution",
                "default",
                List.of(new SystemPromptDocument(
                        "00-base-role.md",
                        "prompts/agent/system/execution/default/00-base-role.md",
                        "你是执行代理"
                )),
                "你是执行代理"
        ));

        List<Map<String, Object>> messages = agentPromptAssembler.buildExecutionMessages(
                task,
                null,
                List.of(),
                "default",
                ""
        );

        assertThat(messages.get(1).get("content").toString())
                .doesNotContain("故事圣经参考：")
                .contains("用户指令：\n只回答用户问题");
    }

    @Test
    void should_load_rewrite_execution_profile_when_requested() {
        AgentGenerationTask task = new AgentGenerationTask();
        task.setTaskType("WORLD_BUILD");
        task.setPromptSnapshot("把这段改写得更凝练");

        when(systemPromptProvider.loadBundle("execution", "rewrite")).thenReturn(new SystemPromptBundle(
                "execution",
                "rewrite",
                List.of(new SystemPromptDocument(
                        "00-base-role.md",
                        "prompts/agent/system/execution/rewrite/00-base-role.md",
                        "你是改写代理"
                )),
                "你是改写代理"
        ));

        List<Map<String, Object>> messages = agentPromptAssembler.buildExecutionMessages(
                task,
                null,
                List.of(),
                "rewrite",
                null
        );

        verify(systemPromptProvider).loadBundle("execution", "rewrite");
        assertThat(messages.get(0))
                .containsEntry("role", "system")
                .containsEntry("content", "你是改写代理");
    }
}
