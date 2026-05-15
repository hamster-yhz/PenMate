package com.penmate.backend.application.rag;

import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import com.penmate.backend.domain.rag.repository.RagDocumentRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagApplicationServiceHybridSearchTest extends BaseApplicationServiceTest {

    private static final String HYBRID_QUERY_TYPE = "com.penmate.backend.application.rag.HybridRagQuery";
    private static final String HYBRID_RESULT_VIEW_TYPE = "com.penmate.backend.application.rag.HybridRagResultView";
    private static final String RAG_SEARCH_SCOPE_TYPE = "com.penmate.backend.application.rag.RagSearchScope";

    @Mock
    private RagDocumentRepository ragDocumentRepository;

    @Mock
    private RagRetrievalService ragRetrievalService;

    @Mock
    private BusinessIdGenerator businessIdGenerator;

    private RagApplicationService ragApplicationService;

    @BeforeEach
    void setUp() {
        ragApplicationService = new RagApplicationService(
                ragDocumentRepository,
                businessIdGenerator,
                ragRetrievalService,
                "http://localhost:9000"
        );
    }

    @Test
    void should_expose_hybrid_query_and_result_contract_for_agent_context_retrieval() {
        Class<?> queryType = loadClass(HYBRID_QUERY_TYPE);
        assertThat(accessorNames(queryType)).contains(
                "projectId",
                "sessionId",
                "taskId",
                "chapterId",
                "storyBibleVersion",
                "activatedSkills",
                "intentTags",
                "userMentionedEntities",
                "topK",
                "queryText",
                "searchScope"
        );

        Class<?> resultType = loadClass(HYBRID_RESULT_VIEW_TYPE);
        assertThat(accessorNames(resultType)).contains(
                "sourceType",
                "sourceId",
                "content",
                "reason",
                "staleFlag",
                "matchedVersion",
                "relevanceScore"
        );

        Class<?> scopeType = loadClass(RAG_SEARCH_SCOPE_TYPE);
        assertThat(scopeType.isEnum()).isTrue();
        assertThat(enumConstantNames(scopeType)).contains("AGENT_CONTEXT");
    }

    @Test
    void should_expose_structured_retrieval_entry_for_hybrid_query() {
        try {
            Method method = RagRetrievalService.class.getDeclaredMethod("retrieve", loadClass(HYBRID_QUERY_TYPE), String.class);
            assertThat(method.getReturnType()).isEqualTo(RagRetrievalService.RetrievalResult.class);
        } catch (NoSuchMethodException ex) {
            fail("Expected method retrieve(%s, String) on %s"
                    .formatted(loadClass(HYBRID_QUERY_TYPE).getSimpleName(), RagRetrievalService.class.getName()));
        }
    }

    @Test
    void should_rank_current_canon_and_explicit_mentions_ahead_of_stale_hits() {
        Object query = newHybridQuery(
                1001L,
                2002L,
                3003L,
                42L,
                3,
                List.of("story_bible_query", "continuity_checker"),
                List.of("CONTINUITY_CHECK", "STORY_BIBLE_QUERY"),
                List.of("林烬", "苏砚"),
                3,
                "核对林烬与苏砚的当前设定"
        );

        when(ragRetrievalService.retrieve((HybridRagQuery) query, "trace-hybrid-rag"))
                .thenReturn(new RagRetrievalService.RetrievalResult(
                        List.of(
                                chunk(
                                        801L,
                                        "story_bible::hero.identity",
                                        1,
                                        "sourceType=story_bible;sourceId=hero.identity;matchedVersion=3;canon=high;entity=林烬;content=林烬是守夜人见习生"
                                ),
                                chunk(
                                        802L,
                                        "chapter::42::su-yan",
                                        2,
                                        "sourceType=chapter;sourceId=chapter-42-su-yan;matchedVersion=3;chapter=42;entity=苏砚;content=苏砚察觉城主身份异常"
                                ),
                                chunk(
                                        803L,
                                        "chapter::45::stale",
                                        3,
                                        "sourceType=chapter;sourceId=chapter-45-stale;matchedVersion=2;chapter=45;entity=苏砚;content=苏砚已经知道林烬身世"
                                )
                        ),
                        7001L
                ));

        List<?> resultViews = invokeHybridSearch(ragApplicationService, query, "trace-hybrid-rag");

        assertThat(resultViews).hasSize(3);
        assertThat(readString(resultViews.get(0), "sourceType")).isEqualTo("story_bible");
        assertThat(readString(resultViews.get(0), "sourceId")).isEqualTo("hero.identity");
        assertThat(readBoolean(resultViews.get(0), "staleFlag")).isFalse();
        assertThat(readString(resultViews.get(0), "reason")).contains("canon").contains("explicit");
        assertThat(readInteger(resultViews.get(0), "matchedVersion")).isEqualTo(3);

        assertThat(readString(resultViews.get(2), "sourceId")).isEqualTo("chapter-45-stale");
        assertThat(readBoolean(resultViews.get(2), "staleFlag")).isTrue();
        assertThat(readInteger(resultViews.get(2), "matchedVersion")).isEqualTo(2);
        assertThat(readDouble(resultViews.get(0), "relevanceScore"))
                .isGreaterThan(readDouble(resultViews.get(2), "relevanceScore"));
    }

    private static List<?> invokeHybridSearch(RagApplicationService service, Object query, String traceId) {
        try {
            Method method = RagApplicationService.class.getDeclaredMethod("hybridSearch", query.getClass(), String.class);
            method.setAccessible(true);
            Object result = method.invoke(service, query, traceId);
            assertThat(result).isInstanceOf(List.class);
            return (List<?>) result;
        } catch (NoSuchMethodException ex) {
            fail("Expected method hybridSearch(%s, String) on %s".formatted(query.getClass().getSimpleName(), RagApplicationService.class.getName()));
            return List.of();
        } catch (Exception ex) {
            fail("Failed to invoke hybridSearch on %s: %s".formatted(RagApplicationService.class.getName(), ex.getMessage()));
            return List.of();
        }
    }

    private static Object newHybridQuery(Long projectId,
                                         Long sessionId,
                                         Long taskId,
                                         Long chapterId,
                                         Integer storyBibleVersion,
                                         List<String> activatedSkills,
                                         List<String> intentTags,
                                         List<String> userMentionedEntities,
                                         Integer topK,
                                         String queryText) {
        try {
            Class<?> queryType = loadClass(HYBRID_QUERY_TYPE);
            Class<?> scopeType = loadClass(RAG_SEARCH_SCOPE_TYPE);
            @SuppressWarnings("unchecked")
            Object scope = Enum.valueOf((Class<? extends Enum>) scopeType.asSubclass(Enum.class), "AGENT_CONTEXT");
            Constructor<?> constructor = queryType.getDeclaredConstructor(
                    Long.class,
                    Long.class,
                    Long.class,
                    Long.class,
                    Integer.class,
                    List.class,
                    List.class,
                    List.class,
                    Integer.class,
                    String.class,
                    scopeType
            );
            constructor.setAccessible(true);
            return constructor.newInstance(
                    projectId,
                    sessionId,
                    taskId,
                    chapterId,
                    storyBibleVersion,
                    activatedSkills,
                    intentTags,
                    userMentionedEntities,
                    topK,
                    queryText,
                    scope
            );
        } catch (NoSuchMethodException ex) {
            fail("Expected canonical constructor on %s".formatted(HYBRID_QUERY_TYPE));
            return null;
        } catch (Exception ex) {
            fail("Failed to instantiate %s: %s".formatted(HYBRID_QUERY_TYPE, ex.getMessage()));
            return null;
        }
    }

    private static RagRetrievedChunk chunk(Long documentId, String title, Integer chunkNo, String contentText) {
        RagRetrievedChunk chunk = new RagRetrievedChunk();
        chunk.setDocumentId(documentId);
        chunk.setDocumentTitle(title);
        chunk.setChunkNo(chunkNo);
        chunk.setContentText(contentText);
        return chunk;
    }

    private static Set<String> accessorNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getParameterCount() == 0)
                .map(Method::getName)
                .collect(Collectors.toSet());
    }

    private static Set<String> enumConstantNames(Class<?> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static String readString(Object target, String accessor) {
        try {
            Object value = target.getClass().getDeclaredMethod(accessor).invoke(target);
            return value == null ? null : value.toString();
        } catch (Exception ex) {
            fail("Failed to read %s from %s: %s".formatted(accessor, target.getClass().getName(), ex.getMessage()));
            return null;
        }
    }

    private static boolean readBoolean(Object target, String accessor) {
        try {
            Object value = target.getClass().getDeclaredMethod(accessor).invoke(target);
            return Boolean.TRUE.equals(value);
        } catch (Exception ex) {
            fail("Failed to read %s from %s: %s".formatted(accessor, target.getClass().getName(), ex.getMessage()));
            return false;
        }
    }

    private static Integer readInteger(Object target, String accessor) {
        try {
            Object value = target.getClass().getDeclaredMethod(accessor).invoke(target);
            return value == null ? null : ((Number) value).intValue();
        } catch (Exception ex) {
            fail("Failed to read %s from %s: %s".formatted(accessor, target.getClass().getName(), ex.getMessage()));
            return null;
        }
    }

    private static double readDouble(Object target, String accessor) {
        try {
            Object value = target.getClass().getDeclaredMethod(accessor).invoke(target);
            return value == null ? 0D : ((Number) value).doubleValue();
        } catch (Exception ex) {
            fail("Failed to read %s from %s: %s".formatted(accessor, target.getClass().getName(), ex.getMessage()));
            return 0D;
        }
    }

    private static Class<?> loadClass(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException ex) {
            fail("Expected class %s to exist".formatted(fqcn));
            return null;
        }
    }
}
