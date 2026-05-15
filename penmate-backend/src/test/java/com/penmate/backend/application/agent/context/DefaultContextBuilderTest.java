package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.orchestration.preflight.AgentBehaviorType;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class DefaultContextBuilderTest {

    private static final String DEFAULT_CONTEXT_BUILDER_TYPE = "com.penmate.backend.application.agent.context.DefaultContextBuilder";
    private static final String CONTEXT_BUDGET_POLICY_TYPE = "com.penmate.backend.application.agent.context.ContextBudgetPolicy";
    private static final String STORY_BIBLE_ENTRY_VIEW_TYPE = "com.penmate.backend.application.agent.context.StoryBibleContextEntryView";
    private static final String HYBRID_RAG_RESULT_VIEW_TYPE = "com.penmate.backend.application.rag.HybridRagResultView";

    @Test
    void should_select_entries_by_task_profile_skills_and_chapter_version_without_loading_everything() {
        Object builder = instantiateBuilder(2);

        ContextPackage contextPackage = invokeBuild(
                builder,
                request(42L, taskProfile(List.of("story_bible_query"))),
                List.of(
                        entryView(
                                "repository",
                                "hero.identity",
                                "主角身份",
                                "林烬是守夜人见习生",
                                "character",
                                "CANON",
                                1,
                                1,
                                null,
                                null
                        ),
                        entryView(
                                "repository",
                                "character.secret.knowledge.linjin",
                                "秘密信息",
                                "只有林烬知道城主其实是他的生父",
                                "character",
                                "CANON",
                                3,
                                1,
                                42L,
                                44L
                        ),
                        entryView(
                                "repository",
                                "character.secret.knowledge.linjin",
                                "秘密信息",
                                "林烬与苏砚都知道城主其实是他的生父",
                                "character",
                                "CANON",
                                3,
                                2,
                                45L,
                                null
                        ),
                        entryView(
                                "repository",
                                "world.rule.taxes",
                                "夜税制度",
                                "港城实行夜税制度",
                                "rule",
                                "CANON",
                                1,
                                1,
                                null,
                                null
                        )
                )
        );

        assertThat(contextPackage.sources()).containsExactly("repository");
        assertThat(contextPackage.missingContextFlags()).isEmpty();
        assertThat(contextPackage.conflicts()).isEmpty();
        assertThat(contextPackage.storyBibleEntries()).hasSize(2);
        assertThat(contextPackage.storyBibleEntries())
                .anySatisfy(entry -> assertThat(entry).contains("hero.identity").contains("林烬是守夜人见习生"))
                .anySatisfy(entry -> assertThat(entry).contains("character.secret.knowledge.linjin").contains("只有林烬知道"));
        assertThat(contextPackage.storyBibleEntries())
                .noneSatisfy(entry -> assertThat(entry).contains("林烬与苏砚都知道"))
                .noneSatisfy(entry -> assertThat(entry).contains("world.rule.taxes"));
        assertThat(contextPackage.chapterScope()).isEqualTo("chapter:42");
    }

    @Test
    void should_treat_hyphenated_story_bible_skill_same_as_snake_case_after_task_profile_normalization() {
        Object builder = instantiateBuilder(2);
        List<Object> entries = List.of(
                entryView(
                        "repository",
                        "hero.identity",
                        "主角身份",
                        "林烬是守夜人见习生",
                        "character",
                        "CANON",
                        1,
                        1,
                        null,
                        null
                ),
                entryView(
                        "repository",
                        "character.secret.knowledge.linjin",
                        "秘密信息",
                        "只有林烬知道城主其实是他的生父",
                        "character",
                        "CANON",
                        3,
                        1,
                        42L,
                        44L
                ),
                entryView(
                        "repository",
                        "world.rule.taxes",
                        "夜税制度",
                        "港城实行夜税制度",
                        "rule",
                        "CANON",
                        1,
                        1,
                        null,
                        null
                )
        );

        ContextPackage snakeCaseContext = invokeBuild(
                builder,
                request(42L, taskProfile(List.of("story_bible_query"))),
                entries
        );
        ContextPackage hyphenatedContext = invokeBuild(
                builder,
                request(42L, taskProfile(List.of("story-bible-query"))),
                entries
        );

        assertThat(hyphenatedContext).isEqualTo(snakeCaseContext);
    }

    @Test
    void should_return_noop_source_and_missing_flag_when_story_bible_entries_are_absent() {
        Object builder = instantiateBuilder(2);

        ContextPackage contextPackage = invokeBuild(
                builder,
                request(3003L, taskProfile(List.of())),
                List.of()
        );

        assertThat(contextPackage.sources()).containsExactly("noop");
        assertThat(contextPackage.missingContextFlags()).containsExactly("story_bible_missing");
        assertThat(contextPackage.storyBibleEntries()).isEmpty();
        assertThat(contextPackage.conflicts()).isEmpty();
    }

    @Test
    void should_mark_conflicts_when_multiple_active_entries_disagree_for_same_key() {
        Object builder = instantiateBuilder(3);

        ContextPackage contextPackage = invokeBuild(
                builder,
                request(42L, taskProfile(List.of("story_bible_query"))),
                List.of(
                        entryView(
                                "repository",
                                "hero.identity",
                                "主角身份",
                                "林烬是守夜人见习生",
                                "character",
                                "CANON",
                                1,
                                1,
                                null,
                                null
                        ),
                        entryView(
                                "repository",
                                "hero.identity",
                                "主角身份",
                                "林烬已经成为守夜人正式成员",
                                "character",
                                "CANON",
                                1,
                                1,
                                null,
                                null
                        )
                )
        );

        assertThat(contextPackage.conflicts())
                .singleElement()
                .satisfies(conflict -> assertThat(conflict).contains("hero.identity"));
        assertThat(contextPackage.storyBibleEntries())
                .singleElement()
                .satisfies(entry -> assertThat(entry).contains("hero.identity"));
    }

    @Test
    void should_consume_hybrid_rag_results_and_exclude_stale_hits_from_context_package() {
        Object builder = instantiateBuilder(3);

        ContextPackage contextPackage = invokeBuildWithRag(
                builder,
                ragRequest(42L, ragTaskProfile(List.of("story_bible_query", "continuity_checker"))),
                List.of(
                        entryView(
                                "repository",
                                "hero.identity",
                                "主角身份",
                                "林烬是守夜人见习生",
                                "character",
                                "CANON",
                                1,
                                3,
                                null,
                                null
                        )
                ),
                List.of(
                        ragResultView(
                                "story_bible",
                                "hero.identity",
                                "林烬是守夜人见习生",
                                "canon+explicit+character",
                                false,
                                3,
                                0.98D
                        ),
                        ragResultView(
                                "chapter",
                                "chapter-42-su-yan",
                                "苏砚察觉城主身份异常",
                                "chapter+entity",
                                false,
                                3,
                                0.87D
                        ),
                        ragResultView(
                                "chapter",
                                "chapter-45-stale",
                                "苏砚已经知道林烬身世",
                                "stale+version_mismatch",
                                true,
                                2,
                                0.31D
                        )
                )
        );

        assertThat(contextPackage.storyBibleEntries())
                .singleElement()
                .satisfies(entry -> assertThat(entry).contains("hero.identity").contains("林烬是守夜人见习生"));
        assertThat(contextPackage.ragRefs()).hasSize(2);
        assertThat(contextPackage.ragRefs())
                .anySatisfy(ref -> assertThat(ref)
                        .contains("story_bible")
                        .contains("hero.identity")
                        .contains("canon+explicit+character")
                        .contains("version=3"))
                .anySatisfy(ref -> assertThat(ref)
                        .contains("chapter")
                        .contains("chapter-42-su-yan")
                        .contains("chapter+entity")
                        .contains("version=3"));
        assertThat(contextPackage.ragRefs())
                .noneSatisfy(ref -> assertThat(ref)
                        .contains("chapter-45-stale")
                        .contains("stale"));
    }

    @Test
    void should_sort_and_deduplicate_rag_refs_before_persisting_context_package() {
        Object builder = instantiateBuilder(3);

        ContextPackage contextPackage = invokeBuildWithRag(
                builder,
                ragRequest(42L, ragTaskProfile(List.of("story_bible_query", "continuity_checker"))),
                List.of(
                        entryView(
                                "repository",
                                "hero.identity",
                                "主角身份",
                                "林烬是守夜人见习生",
                                "character",
                                "CANON",
                                1,
                                3,
                                null,
                                null
                        )
                ),
                List.of(
                        ragResultView(
                                "chapter",
                                "chapter-42-su-yan",
                                "苏砚察觉城主身份异常",
                                "chapter+entity",
                                false,
                                3,
                                0.41D
                        ),
                        ragResultView(
                                "story_bible",
                                "hero.identity",
                                "林烬是守夜人见习生",
                                "canon+explicit+character",
                                false,
                                3,
                                0.98D
                        ),
                        ragResultView(
                                "story_bible",
                                "hero.identity",
                                "重复低分片段",
                                "keyword",
                                false,
                                3,
                                0.12D
                        )
                )
        );

        assertThat(contextPackage.ragRefs()).hasSize(2);
        assertThat(contextPackage.ragRefs().get(0)).contains("hero.identity");
        assertThat(contextPackage.ragRefs().get(1)).contains("chapter-42-su-yan");
        assertThat(contextPackage.ragRefs())
                .noneSatisfy(ref -> assertThat(ref).contains("重复低分片段"));
    }

    private static Object instantiateBuilder(int maxStoryBibleEntries) {
        try {
            Class<?> builderType = Class.forName(DEFAULT_CONTEXT_BUILDER_TYPE);
            Class<?> policyType = Class.forName(CONTEXT_BUDGET_POLICY_TYPE);
            Object policy = instantiatePolicy(policyType, maxStoryBibleEntries);
            List<Constructor<?>> constructors = new ArrayList<>(List.of(builderType.getDeclaredConstructors()));
            constructors.sort((left, right) -> Integer.compare(right.getParameterCount(), left.getParameterCount()));
            for (Constructor<?> constructor : constructors) {
                constructor.setAccessible(true);
                if (constructor.getParameterCount() == 1
                        && constructor.getParameterTypes()[0].isAssignableFrom(policyType)) {
                    return constructor.newInstance(policy);
                }
                if (constructor.getParameterCount() == 0) {
                    return constructor.newInstance();
                }
            }
            fail("Expected constructor on %s with zero args or ContextBudgetPolicy".formatted(DEFAULT_CONTEXT_BUILDER_TYPE));
            return null;
        } catch (ClassNotFoundException ex) {
            fail("Expected class %s to exist".formatted(DEFAULT_CONTEXT_BUILDER_TYPE));
            return null;
        } catch (Exception ex) {
            fail("Failed to instantiate %s: %s".formatted(DEFAULT_CONTEXT_BUILDER_TYPE, ex.getMessage()));
            return null;
        }
    }

    private static Object instantiatePolicy(Class<?> policyType, int maxStoryBibleEntries) {
        try {
            for (Constructor<?> constructor : policyType.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                if (constructor.getParameterCount() == 1) {
                    Class<?> parameterType = constructor.getParameterTypes()[0];
                    if (parameterType == int.class || parameterType == Integer.class) {
                        return constructor.newInstance(maxStoryBibleEntries);
                    }
                }
                if (constructor.getParameterCount() == 0) {
                    return constructor.newInstance();
                }
            }
            fail("Expected constructor on %s with zero args or max entry count".formatted(CONTEXT_BUDGET_POLICY_TYPE));
            return null;
        } catch (Exception ex) {
            fail("Failed to instantiate %s: %s".formatted(CONTEXT_BUDGET_POLICY_TYPE, ex.getMessage()));
            return null;
        }
    }

    private static ContextPackage invokeBuild(Object builder,
                                              AgentContextRoutingRequest request,
                                              List<Object> entryViews) {
        Method method = findMethod(builder.getClass(), "build", 2);
        try {
            Object result = method.invoke(builder, request, entryViews);
            assertThat(result).isInstanceOf(ContextPackage.class);
            return (ContextPackage) result;
        } catch (Exception ex) {
            fail("Failed to invoke build on %s: %s".formatted(builder.getClass().getName(), ex.getMessage()));
            return null;
        }
    }

    private static ContextPackage invokeBuildWithRag(Object builder,
                                                     AgentContextRoutingRequest request,
                                                     List<Object> entryViews,
                                                     List<Object> ragResultViews) {
        Method method = findMethod(builder.getClass(), "build", 3);
        try {
            Object result = method.invoke(builder, request, entryViews, ragResultViews);
            assertThat(result).isInstanceOf(ContextPackage.class);
            return (ContextPackage) result;
        } catch (Exception ex) {
            fail("Failed to invoke build(request, storyBibleEntries, ragResults) on %s: %s"
                    .formatted(builder.getClass().getName(), ex.getMessage()));
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String methodName, int parameterCount) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                method.setAccessible(true);
                return method;
            }
        }
        fail("Expected method %s(%s args) on %s".formatted(methodName, parameterCount, type.getName()));
        return null;
    }

    private static Object entryView(String source,
                                    String entryKey,
                                    String title,
                                    String content,
                                    String entryType,
                                    String canonicalStatus,
                                    Integer riskLevel,
                                    Integer versionNo,
                                    Long validFromChapterId,
                                    Long validToChapterId) {
        try {
            Class<?> viewType = Class.forName(STORY_BIBLE_ENTRY_VIEW_TYPE);
            Constructor<?> constructor = viewType.getDeclaredConstructor(
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    Integer.class,
                    Integer.class,
                    Long.class,
                    Long.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                    source,
                    entryKey,
                    title,
                    content,
                    entryType,
                    canonicalStatus,
                    riskLevel,
                    versionNo,
                    validFromChapterId,
                    validToChapterId
            );
        } catch (ClassNotFoundException ex) {
            fail("Expected class %s to exist".formatted(STORY_BIBLE_ENTRY_VIEW_TYPE));
            return null;
        } catch (NoSuchMethodException ex) {
            fail("Expected canonical constructor on %s".formatted(STORY_BIBLE_ENTRY_VIEW_TYPE));
            return null;
        } catch (Exception ex) {
            fail("Failed to instantiate %s: %s".formatted(STORY_BIBLE_ENTRY_VIEW_TYPE, ex.getMessage()));
            return null;
        }
    }

    private static Object ragResultView(String sourceType,
                                        String sourceId,
                                        String content,
                                        String reason,
                                        boolean staleFlag,
                                        Integer matchedVersion,
                                        Double relevanceScore) {
        try {
            Class<?> viewType = Class.forName(HYBRID_RAG_RESULT_VIEW_TYPE);
            for (Constructor<?> constructor : viewType.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == 7) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(
                            sourceType,
                            sourceId,
                            content,
                            reason,
                            staleFlag,
                            matchedVersion,
                            relevanceScore
                    );
                }
            }
            fail("Expected canonical constructor on %s".formatted(HYBRID_RAG_RESULT_VIEW_TYPE));
            return null;
        } catch (ClassNotFoundException ex) {
            fail("Expected class %s to exist".formatted(HYBRID_RAG_RESULT_VIEW_TYPE));
            return null;
        } catch (Exception ex) {
            fail("Failed to instantiate %s: %s".formatted(HYBRID_RAG_RESULT_VIEW_TYPE, ex.getMessage()));
            return null;
        }
    }

    private static AgentContextRoutingRequest request(Long chapterId, TaskProfile taskProfile) {
        return new AgentContextRoutingRequest(
                1001L,
                2002L,
                chapterId,
                "请检查角色设定与秘密信息是否一致",
                "{\"style\":\"noir\"}",
                new AgentPreflightDecision(
                        AgentBehaviorType.STORY_BIBLE_QUERY_CANDIDATE,
                        "default",
                        false,
                        false,
                        true,
                        "需要 story bible",
                        "{\"includeStoryBibleContext\":true}"
                ),
                taskProfile
        );
    }

    private static AgentContextRoutingRequest ragRequest(Long chapterId, TaskProfile taskProfile) {
        return new AgentContextRoutingRequest(
                1001L,
                2002L,
                chapterId,
                "核对林烬与苏砚的当前设定并检查章节版本是否过期",
                "{\"style\":\"noir\"}",
                new AgentPreflightDecision(
                        AgentBehaviorType.STORY_BIBLE_QUERY_CANDIDATE,
                        "default",
                        false,
                        true,
                        true,
                        "需要 story bible 与 rag context",
                        "{\"includeStoryBibleContext\":true,\"includeRagContext\":true}",
                        List.of("CONTINUITY_CHECK", "STORY_BIBLE_QUERY"),
                        List.of(),
                        List.of("story_bible_query", "continuity_checker"),
                        List.of(),
                        "输出一致性检查结论",
                        false,
                        false,
                        false
                ),
                taskProfile
        );
    }

    private static TaskProfile taskProfile(List<String> skills) {
        return new TaskProfile(
                List.of(),
                "default",
                skills,
                List.of(),
                List.of(),
                null,
                false,
                true,
                false,
                "test"
        );
    }

    private static TaskProfile ragTaskProfile(List<String> skills) {
        return new TaskProfile(
                List.of(),
                "default",
                skills,
                List.of(),
                List.of(),
                "输出一致性检查结论",
                false,
                true,
                true,
                "rag+story bible context"
        );
    }
}
