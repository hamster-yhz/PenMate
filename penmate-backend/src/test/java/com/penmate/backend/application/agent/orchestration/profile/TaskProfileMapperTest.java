package com.penmate.backend.application.agent.orchestration.profile;

import com.penmate.backend.application.agent.orchestration.preflight.AgentBehaviorType;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskProfileMapperTest {

    @Test
    void should_map_structured_preflight_decision_into_stable_task_profile() {
        AgentPreflightDecision decision = newDecision(
                AgentBehaviorType.WRITE,
                "default",
                true,
                true,
                true,
                "用户既要续写也要核对设定并保持风格一致",
                "{\"trace\":true}",
                List.of("DRAFT_GENERATION", "CONTINUITY_CHECK", "STYLE_ALIGNMENT"),
                List.of("保留第一人称", "不得改写既有设定"),
                List.of("scene-writer", "story-bible-guard"),
                List.of("draft_generation", "story_bible_lookup"),
                "输出一段可直接进入正文的中文续写",
                true,
                true,
                false
        );

        TaskProfile profile = TaskProfileMapper.from(decision);

        assertThat(profile.intentTags()).containsExactly(
                TaskIntentTag.DRAFT_GENERATION,
                TaskIntentTag.CONTINUITY_CHECK,
                TaskIntentTag.STYLE_ALIGNMENT
        );
        assertThat(profile.executionProfile()).isEqualTo("default");
        assertThat(profile.skills()).containsExactly("scene_writer", "story_bible_guard");
        assertThat(profile.tools()).containsExactly("draft_generation", "story_bible_lookup");
        assertThat(profile.hardConstraints()).containsExactly("保留第一人称", "不得改写既有设定");
        assertThat(profile.outputExpectation()).isEqualTo("输出一段可直接进入正文的中文续写");
        assertThat(profile.needsApproval()).isTrue();
        assertThat(profile.includeStoryBible()).isTrue();
        assertThat(profile.includeRag()).isTrue();
        assertThat(profile.reasoningSummary()).isEqualTo("用户既要续写也要核对设定并保持风格一致");
    }

    @Test
    void should_append_story_bible_query_and_clarification_intents_from_decision_flags() {
        AgentPreflightDecision decision = newDecision(
                AgentBehaviorType.STORY_BIBLE_QUERY_CANDIDATE,
                "default",
                false,
                false,
                false,
                "需要先澄清人物指代并标记后续更新 story bible",
                "{\"trace\":true}",
                List.of("CONTINUITY_CHECK"),
                List.of(),
                List.of("clarifier"),
                List.of(),
                "先向用户确认是母亲还是养母",
                false,
                true,
                true
        );

        TaskProfile profile = TaskProfileMapper.from(decision);

        assertThat(profile.intentTags()).containsExactly(
                TaskIntentTag.CONTINUITY_CHECK,
                TaskIntentTag.STORY_BIBLE_QUERY,
                TaskIntentTag.CLARIFICATION
        );
        assertThat(profile.includeStoryBible()).isTrue();
        assertThat(profile.includeRag()).isFalse();
        assertThat(profile.needsApproval()).isFalse();
    }

    @Test
    void should_reject_unknown_intent_tag_values_from_preflight_decision() {
        AgentPreflightDecision decision = newDecision(
                AgentBehaviorType.WRITE,
                "default",
                false,
                false,
                false,
                "非法意图标签应该阻止进入稳定 TaskProfile",
                "{\"trace\":true}",
                List.of("DRAFT_GENERATION", "UNKNOWN_TAG"),
                List.of(),
                List.of(),
                List.of(),
                null,
                false,
                false,
                false
        );

        assertThatThrownBy(() -> TaskProfileMapper.from(decision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN_TAG");
    }

    private static AgentPreflightDecision newDecision(AgentBehaviorType behaviorType,
                                                      String executionPromptProfile,
                                                      boolean includeStyleContext,
                                                      boolean includeRagContext,
                                                      boolean includeStoryBibleContext,
                                                      String reasoningSummary,
                                                      String decisionTraceJson,
                                                      List<String> intentTags,
                                                      List<String> hardConstraints,
                                                      List<String> enabledSkills,
                                                      List<String> enabledTools,
                                                      String outputExpectation,
                                                      boolean needsApproval,
                                                      boolean needsStoryBibleUpdate,
                                                      boolean needsClarification) {
        try {
            Constructor<AgentPreflightDecision> constructor = AgentPreflightDecision.class.getDeclaredConstructor(
                    AgentBehaviorType.class,
                    String.class,
                    boolean.class,
                    boolean.class,
                    boolean.class,
                    String.class,
                    String.class,
                    List.class,
                    List.class,
                    List.class,
                    List.class,
                    String.class,
                    boolean.class,
                    boolean.class,
                    boolean.class
            );
            return constructor.newInstance(
                    behaviorType,
                    executionPromptProfile,
                    includeStyleContext,
                    includeRagContext,
                    includeStoryBibleContext,
                    reasoningSummary,
                    decisionTraceJson,
                    intentTags,
                    hardConstraints,
                    enabledSkills,
                    enabledTools,
                    outputExpectation,
                    needsApproval,
                    needsStoryBibleUpdate,
                    needsClarification
            );
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Expected extended AgentPreflightDecision constructor to exist", ex);
        }
    }
}
