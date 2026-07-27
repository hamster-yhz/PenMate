package com.penmate.backend.infrastructure.agent.prompt;

import com.penmate.backend.application.agent.prompt.SystemPromptBundle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClasspathMarkdownSystemPromptProviderTest {

    private final ClasspathMarkdownSystemPromptProvider provider = new ClasspathMarkdownSystemPromptProvider();

    @Test
    void loads_the_single_execution_bundle_from_its_manifest() {
        SystemPromptBundle bundle = provider.loadBundle("execution");

        assertThat(bundle.stage()).isEqualTo("execution");
        assertThat(bundle.documents()).extracting(document -> document.path()).containsExactly(
                "prompts/agent/system/execution/_base/00-identity.md",
                "prompts/agent/system/execution/_base/10-authority-and-context.md",
                "prompts/agent/system/execution/default/00-base-role.md",
                "prompts/agent/system/execution/_base/20-working-contract.md",
                "prompts/agent/system/execution/_base/30-tool-and-state-policy.md",
                "prompts/agent/system/execution/_base/40-output-contract.md"
        );
        assertThat(bundle.assembledPrompt()).contains("ledger_crud", "story_bible_inspect")
                .contains("story_bible_structure_write", "必须检查完整 catalog", "不得为了单个节点")
                .doesNotContain("todo_crud");
    }
}
