package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.orchestration.preflight.AgentBehaviorType;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import com.penmate.backend.application.rag.HybridRagQuery;
import com.penmate.backend.application.rag.HybridRagResultView;
import com.penmate.backend.application.rag.RagApplicationService;
import com.penmate.backend.application.rag.RagSearchScope;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class DefaultAgentContextRoutingFacadeTest {

    private static final String STORY_BIBLE_ENTRY_VIEW_TYPE = "com.penmate.backend.application.agent.context.StoryBibleContextEntryView";

    @Test
    void should_route_structured_story_bible_entries_into_context_package_when_story_bible_context_is_enabled() {
        assertStructuredStoryBibleProviderContract();
        AtomicInteger loadCalls = new AtomicInteger();
        StoryBibleContextProvider provider = proxyProvider(loadCalls, List.of(
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
                )
        ));
        DefaultAgentContextRoutingFacade facade = new DefaultAgentContextRoutingFacade(provider);

        AgentContextRoutingResult result = facade.route(new AgentContextRoutingRequest(
                1001L,
                2002L,
                42L,
                "请检查角色设定与秘密信息是否一致",
                "{\"style\":\"noir\"}",
                decision(true, true),
                taskProfile(List.of("story_bible_query"))
        ));

        assertThat(loadCalls.get()).isEqualTo(1);
        assertThat(result.styleSnapshot()).isEqualTo("{\"style\":\"noir\"}");
        ContextPackage contextPackage = extractContextPackage(result);
        assertThat(contextPackage.sources()).contains("repository");
        assertThat(contextPackage.storyBibleEntries())
                .anySatisfy(entry -> assertThat(entry).contains("hero.identity").contains("林烬是守夜人见习生"))
                .anySatisfy(entry -> assertThat(entry).contains("character.secret.knowledge.linjin").contains("只有林烬知道"));
        assertThat(contextPackage.chapterScope()).isEqualTo("chapter:42");
    }

    @Test
    void should_return_noop_source_and_missing_flag_when_story_bible_is_requested_but_provider_returns_no_entries() {
        assertStructuredStoryBibleProviderContract();
        DefaultAgentContextRoutingFacade facade = new DefaultAgentContextRoutingFacade(proxyProvider(new AtomicInteger(), List.of()));

        AgentContextRoutingResult result = facade.route(new AgentContextRoutingRequest(
                1001L,
                2002L,
                3003L,
                "请续写雨夜回城后的场景",
                "{\"style\":\"noir\"}",
                decision(false, true),
                taskProfile(List.of())
        ));

        ContextPackage contextPackage = extractContextPackage(result);
        assertThat(contextPackage.sources()).containsExactly("noop");
        assertThat(contextPackage.missingContextFlags()).containsExactly("story_bible_missing");
        assertThat(contextPackage.storyBibleEntries()).isEmpty();
    }

    @Test
    void should_not_call_story_bible_provider_when_preflight_disables_story_bible_context() {
        AtomicInteger loadCalls = new AtomicInteger();
        DefaultAgentContextRoutingFacade facade = new DefaultAgentContextRoutingFacade(proxyProvider(loadCalls, List.of()));

        AgentContextRoutingResult result = facade.route(new AgentContextRoutingRequest(
                1001L,
                2002L,
                3003L,
                "请续写雨夜回城后的场景",
                "{\"style\":\"noir\"}",
                decision(false, false),
                taskProfile(List.of())
        ));

        assertThat(loadCalls.get()).isZero();
        assertThat(result.styleSnapshot()).isNull();
    }

    @Test
    void should_expose_story_bible_provider_as_structured_entry_view_contract() {
        assertStructuredStoryBibleProviderContract();
    }

    @Test
    void should_forward_structured_rag_query_fields_when_rag_context_is_enabled() {
        AtomicReference<HybridRagQuery> capturedQuery = new AtomicReference<>();
        RagApplicationService ragApplicationService = new RagApplicationService(null, null, null, "http://localhost:9000") {
            @Override
            public List<HybridRagResultView> hybridSearch(HybridRagQuery query, String traceId) {
                capturedQuery.set(query);
                return List.of(new HybridRagResultView(
                        "story_bible",
                        "hero.identity",
                        "林烬是守夜人见习生",
                        "canon+explicit+character",
                        false,
                        3,
                        0.98D
                ));
            }
        };
        DefaultAgentContextRoutingFacade facade = new DefaultAgentContextRoutingFacade(
                proxyProvider(new AtomicInteger(), List.of(
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
                )),
                new DefaultContextBuilder(),
                ragApplicationService
        );

        AgentContextRoutingResult result = facade.route(ragRoutingRequest());

        assertThat(capturedQuery.get()).isNotNull();
        assertThat(capturedQuery.get().projectId()).isEqualTo(1001L);
        assertThat(capturedQuery.get().sessionId()).isEqualTo(9009L);
        assertThat(capturedQuery.get().taskId()).isEqualTo(8008L);
        assertThat(capturedQuery.get().chapterId()).isEqualTo(42L);
        assertThat(capturedQuery.get().storyBibleVersion()).isEqualTo(3);
        assertThat(capturedQuery.get().activatedSkills()).containsExactly("story_bible_query", "continuity_checker");
        assertThat(capturedQuery.get().intentTags()).containsExactly("CONTINUITY_CHECK", "STORY_BIBLE_QUERY");
        assertThat(capturedQuery.get().userMentionedEntities()).containsExactly("林烬", "苏砚");
        assertThat(capturedQuery.get().searchScope()).isEqualTo(RagSearchScope.AGENT_CONTEXT);
        assertThat(extractContextPackage(result).ragRefs()).singleElement().satisfies(ref -> assertThat(ref).contains("hero.identity"));
    }

    private static void assertStructuredStoryBibleProviderContract() {
        try {
            Method method = StoryBibleContextProvider.class.getDeclaredMethod(
                    "loadContext",
                    Long.class,
                    Long.class,
                    Long.class,
                    String.class,
                    AgentPreflightDecision.class
            );
            Type genericReturnType = method.getGenericReturnType();
            assertThat(method.getReturnType()).isEqualTo(List.class);
            assertThat(genericReturnType.getTypeName()).contains("StoryBibleContextEntryView");
        } catch (NoSuchMethodException ex) {
            fail("Expected StoryBibleContextProvider.loadContext(...) to exist: %s".formatted(ex.getMessage()));
        }
    }

    private static StoryBibleContextProvider proxyProvider(AtomicInteger loadCalls, List<Object> entryViews) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "StoryBibleContextProviderProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }
            if ("loadContext".equals(method.getName())) {
                loadCalls.incrementAndGet();
                return entryViews;
            }
            throw new UnsupportedOperationException("Unexpected method: " + method.getName());
        };
        return (StoryBibleContextProvider) Proxy.newProxyInstance(
                StoryBibleContextProvider.class.getClassLoader(),
                new Class<?>[]{StoryBibleContextProvider.class},
                handler
        );
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

    private static ContextPackage extractContextPackage(AgentContextRoutingResult result) {
        try {
            Method accessor = result.getClass().getDeclaredMethod("contextPackage");
            Object value = accessor.invoke(result);
            assertThat(value).isInstanceOf(ContextPackage.class);
            return (ContextPackage) value;
        } catch (NoSuchMethodException ex) {
            fail("Expected AgentContextRoutingResult.contextPackage() to exist");
            return null;
        } catch (Exception ex) {
            fail("Failed to read AgentContextRoutingResult.contextPackage(): %s".formatted(ex.getMessage()));
            return null;
        }
    }

    private static AgentContextRoutingRequest ragRoutingRequest() {
        try {
            Constructor<AgentContextRoutingRequest> constructor = AgentContextRoutingRequest.class.getDeclaredConstructor(
                    Long.class,
                    Long.class,
                    Long.class,
                    Long.class,
                    Long.class,
                    Integer.class,
                    List.class,
                    String.class,
                    String.class,
                    AgentPreflightDecision.class,
                    TaskProfile.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                    1001L,
                    2002L,
                    9009L,
                    8008L,
                    42L,
                    3,
                    List.of("林烬", "苏砚"),
                    "核对林烬与苏砚的当前设定并检查章节版本是否过期",
                    "{\"style\":\"noir\"}",
                    ragDecision(),
                    ragTaskProfile()
            );
        } catch (NoSuchMethodException ex) {
            fail("Expected canonical constructor on %s with sessionId/taskId/storyBibleVersion/userMentionedEntities"
                    .formatted(AgentContextRoutingRequest.class.getName()));
            return null;
        } catch (Exception ex) {
            fail("Failed to instantiate %s: %s".formatted(AgentContextRoutingRequest.class.getName(), ex.getMessage()));
            return null;
        }
    }

    private static AgentPreflightDecision decision(boolean includeStyleContext, boolean includeStoryBibleContext) {
        return new AgentPreflightDecision(
                AgentBehaviorType.WRITE,
                "default",
                includeStyleContext,
                false,
                includeStoryBibleContext,
                "context-builder test",
                "{\"includeStoryBibleContext\":%s}".formatted(includeStoryBibleContext)
        );
    }

    private static AgentPreflightDecision ragDecision() {
        return new AgentPreflightDecision(
                AgentBehaviorType.STORY_BIBLE_QUERY_CANDIDATE,
                "default",
                false,
                true,
                true,
                "context-builder rag routing test",
                "{\"includeRagContext\":true,\"includeStoryBibleContext\":true}",
                List.of("CONTINUITY_CHECK", "STORY_BIBLE_QUERY"),
                List.of(),
                List.of("story_bible_query", "continuity_checker"),
                List.of(),
                "输出一致性检查结论",
                false,
                false,
                false
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

    private static TaskProfile ragTaskProfile() {
        return new TaskProfile(
                List.of(),
                "default",
                List.of("story_bible_query", "continuity_checker"),
                List.of(),
                List.of(),
                "输出一致性检查结论",
                false,
                true,
                true,
                "rag test"
        );
    }
}
