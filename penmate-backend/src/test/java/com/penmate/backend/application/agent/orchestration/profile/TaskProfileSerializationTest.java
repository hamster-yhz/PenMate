package com.penmate.backend.application.agent.orchestration.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.prompt.PromptModulePlan;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import com.penmate.backend.application.storybible.StoryBibleUpdateProposal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskProfileSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void should_round_trip_task_profile_with_stable_prd_field_names() throws Exception {
        TaskProfile profile = new TaskProfile(
                List.of(TaskIntentTag.DRAFT_GENERATION, TaskIntentTag.STORY_BIBLE_QUERY),
                "default",
                List.of("scene-writer", "consistency-checker"),
                List.of("draft_generation", "story_bible_lookup"),
                List.of("保留第一人称", "不得改写既有设定"),
                "输出一段可直接进入正文的中文场景续写",
                true,
                true,
                false,
                "用户同时要求续写正文并核对设定一致性"
        );

        String json = objectMapper.writeValueAsString(profile);
        Map<String, Object> tree = objectMapper.readValue(json, Map.class);
        TaskProfile restored = objectMapper.readValue(json, TaskProfile.class);

        assertThat(tree).containsOnlyKeys(
                "intentTags",
                "executionProfile",
                "skills",
                "tools",
                "hardConstraints",
                "outputExpectation",
                "needsApproval",
                "includeStoryBible",
                "includeRag",
                "reasoningSummary"
        );
        assertThat(tree.get("executionProfile")).isEqualTo("default");
        assertThat(tree.get("needsApproval")).isEqualTo(true);
        assertThat(tree.get("includeStoryBible")).isEqualTo(true);
        assertThat(tree.get("includeRag")).isEqualTo(false);
        assertThat(tree.get("reasoningSummary")).isEqualTo("用户同时要求续写正文并核对设定一致性");
        assertThat(restored).isEqualTo(profile);
    }

    @Test
    void should_round_trip_prompt_plan_with_modules_skills_final_profile_and_preview() throws Exception {
        PromptPlan plan = new PromptPlan(
                List.of(
                        new PromptModulePlan("base-role", "system/base-role.md", true, "定义主编排角色"),
                        new PromptModulePlan("story-bible", "system/story-bible.md", true, "注入设定约束")
                ),
                List.of("scene-writer", "story-bible-guard"),
                "default",
                "# assembled prompt preview"
        );

        String json = objectMapper.writeValueAsString(plan);
        Map<String, Object> tree = objectMapper.readValue(json, Map.class);
        PromptPlan restored = objectMapper.readValue(json, PromptPlan.class);

        assertThat(tree).containsOnlyKeys("modules", "skills", "finalProfile", "assembledPromptPreview");
        assertThat(tree.get("finalProfile")).isEqualTo("default");
        assertThat(tree.get("assembledPromptPreview")).isEqualTo("# assembled prompt preview");
        assertThat(restored).isEqualTo(plan);
    }

    @Test
    void should_deserialize_raw_task_profile_json_and_preserve_null_vs_present_values() throws Exception {
        String json = """
                {
                  "intentTags": ["DRAFT_GENERATION", "STORY_BIBLE_QUERY"],
                  "executionProfile": "default",
                  "skills": ["scene-writer"],
                  "tools": ["story_bible_lookup"],
                  "hardConstraints": ["保留第一人称"],
                  "outputExpectation": null,
                  "needsApproval": false,
                  "includeStoryBible": true,
                  "includeRag": false,
                  "reasoningSummary": "  需要核对设定  "
                }
                """;

        TaskProfile restored = objectMapper.readValue(json, TaskProfile.class);

        assertThat(restored.intentTags()).containsExactly(TaskIntentTag.DRAFT_GENERATION, TaskIntentTag.STORY_BIBLE_QUERY);
        assertThat(restored.executionProfile()).isEqualTo("default");
        assertThat(restored.outputExpectation()).isNull();
        assertThat(restored.reasoningSummary()).isEqualTo("需要核对设定");
        assertThat(restored.skills()).isUnmodifiable();
    }

    @Test
    void should_deserialize_raw_prompt_plan_json_with_missing_lists_as_empty_lists() throws Exception {
        String json = """
                {
                  "modules": null,
                  "skills": null,
                  "finalProfile": " default ",
                  "assembledPromptPreview": "  # preview  "
                }
                """;

        PromptPlan restored = objectMapper.readValue(json, PromptPlan.class);

        assertThat(restored.modules()).isEmpty();
        assertThat(restored.skills()).isEmpty();
        assertThat(restored.finalProfile()).isEqualTo("default");
        assertThat(restored.assembledPromptPreview()).isEqualTo("# preview");
    }

    @Test
    void should_round_trip_story_bible_update_proposal_without_alias_names() throws Exception {
        StoryBibleUpdateProposal proposal = new StoryBibleUpdateProposal(
                List.of("hero.identity", "world.taboo"),
                "proposed",
                "本轮生成新增了主角身份与禁忌规则，需进入后续审核"
        );

        String json = objectMapper.writeValueAsString(proposal);
        Map<String, Object> tree = objectMapper.readValue(json, Map.class);
        StoryBibleUpdateProposal restored = objectMapper.readValue(json, StoryBibleUpdateProposal.class);

        assertThat(tree).containsOnlyKeys("entryKeys", "canonicalStatus", "reasoningSummary");
        assertThat(tree.get("canonicalStatus")).isEqualTo("proposed");
        assertThat(restored).isEqualTo(proposal);
    }
}
