package com.penmate.backend.infrastructure.agent.prompt;

import com.penmate.backend.application.agent.prompt.SystemPromptBundle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathMarkdownSystemPromptProviderTest {

    private final ClasspathMarkdownSystemPromptProvider provider = new ClasspathMarkdownSystemPromptProvider();

    @Test
    void should_load_markdown_bundle_from_classpath_in_lexicographic_order() {
        SystemPromptBundle bundle = provider.loadBundle("execution", "default");

        assertThat(bundle.stage()).isEqualTo("execution");
        assertThat(bundle.profile()).isEqualTo("default");
        assertThat(bundle.documents())
                .extracting(document -> document.fileName())
                .containsExactly(
                        "00-base-role.md",
                        "10-writing-rules.md",
                        "20-tool-use-policy.md"
                );
        assertThat(bundle.documents())
                .extracting(document -> document.path())
                .containsExactly(
                        "prompts/agent/system/execution/default/00-base-role.md",
                        "prompts/agent/system/execution/default/10-writing-rules.md",
                        "prompts/agent/system/execution/default/20-tool-use-policy.md"
                );
        assertThat(bundle.assembledPrompt()).isEqualTo(String.join(
                "\n\n",
                bundle.documents().stream().map(document -> document.content()).toList()
        ));
        assertThat(bundle.assembledPrompt())
                .contains("你是 PenMate 的执行阶段写作代理")
                .contains("优先保证输出内容可直接服务于小说创作")
                .contains("当且仅当现有上下文不足以完成任务时，才调用工具");
    }

    @Test
    void should_fail_when_prompt_profile_directory_does_not_exist() {
        assertThatThrownBy(() -> provider.loadBundle("execution", "missing-profile"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompts/agent/system/execution/missing-profile");
    }
}
