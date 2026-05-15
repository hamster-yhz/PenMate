package com.penmate.backend.application.storybible;

import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.storybible.model.StoryBibleEntry;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryBibleApplicationServiceTest extends BaseApplicationServiceTest {

    private static final String APPLICATION_SERVICE = "com.penmate.backend.application.storybible.StoryBibleApplicationService";

    @Test
    void UT_APP_STORY_BIBLE_SHOULD_INITIALIZE_PROPOSED_DRAFT_FROM_ONE_LINE_IDEA() {
        Object service = newApplicationService(List.of());

        List<?> proposals = invokeListMethod(service,
                "initializeFromIdea",
                1001L,
                "失忆的守夜人在会说话的灯塔里寻找自己的名字"
        );

        assertThat(proposals).isNotEmpty();
        assertThat(proposals)
                .allSatisfy(proposal -> {
                    assertThat(stringProperty(proposal, "canonicalStatus")).isEqualTo("PROPOSED");
                    assertThat(stringProperty(proposal, "entryKey")).isNotBlank();
                    assertThat(stringProperty(proposal, "entryType")).isNotBlank();
                    assertThat(stringProperty(proposal, "sourceText"))
                            .contains("失忆的守夜人")
                            .contains("灯塔");
                    assertThat(stringProperty(proposal, "inferenceLevel")).isNotBlank();
                });
    }

    @Test
    void UT_APP_STORY_BIBLE_SHOULD_EXTRACT_CHARACTER_LOCATION_EVENT_AND_INFORMATION_BOUNDARY_PROPOSALS_FROM_CHAPTER() {
        Object service = newApplicationService(List.of());
        String chapterText = "第42章，林烬独自潜入雾港钟楼。只有林烬知道，城主其实是他的生父。钟楼守门人白檀严禁任何人触碰海灯。黎明前，林烬在钟楼顶层与追兵交手并打碎了照海镜。";

        List<?> proposals = invokeListMethod(service,
                "proposeUpdatesFromChapter",
                1001L,
                42L,
                chapterText
        );

        assertThat(proposals).hasSizeGreaterThanOrEqualTo(4);
        assertThat(proposals)
                .extracting(proposal -> stringProperty(proposal, "entryType"))
                .contains("character", "location", "event", "information_boundary");
        assertThat(proposals)
                .allSatisfy(proposal -> {
                    assertThat(longProperty(proposal, "sourceChapterId")).isEqualTo(42L);
                    assertThat(stringProperty(proposal, "canonicalStatus")).isEqualTo("PROPOSED");
                    assertThat(stringProperty(proposal, "sourceText")).isNotBlank();
                    assertThat(stringProperty(proposal, "inferenceLevel")).isNotBlank();
                });
        assertThat(proposals)
                .filteredOn(proposal -> "information_boundary".equals(stringProperty(proposal, "entryType")))
                .anySatisfy(proposal -> assertThat(stringProperty(proposal, "proposedContent")).contains("只有林烬知道"));
    }

    @Test
    void UT_APP_STORY_BIBLE_SHOULD_KEEP_HIGH_RISK_UPDATES_AS_PROPOSALS_INSTEAD_OF_AUTO_CANON() {
        Object service = newApplicationService(List.of());
        String chapterText = "第45章，林烬向苏砚坦白城主其实是他的生父，从此这个秘密不再只属于他一人。";

        List<?> proposals = invokeListMethod(service,
                "proposeUpdatesFromChapter",
                1001L,
                45L,
                chapterText
        );

        assertThat(proposals).isNotEmpty();
        assertThat(proposals)
                .filteredOn(proposal -> integerProperty(proposal, "riskLevel") >= 3)
                .isNotEmpty()
                .allSatisfy(proposal -> assertThat(stringProperty(proposal, "canonicalStatus")).isEqualTo("PROPOSED"));
        assertThat(proposals)
                .extracting(proposal -> stringProperty(proposal, "canonicalStatus"))
                .doesNotContain("CANON");
    }

    @Test
    void UT_APP_STORY_BIBLE_SHOULD_KEEP_CANON_FACT_WHEN_HIGHER_VERSION_PROPOSAL_EXISTS_FOR_SAME_KEY() {
        StoryBibleEntry canon = entry(
                "hero.identity",
                "character",
                "林烬是守夜人见习生",
                "CANON",
                1,
                null,
                null,
                1
        );
        StoryBibleEntry proposal = entry(
                "hero.identity",
                "character",
                "林烬可能是城主私生子",
                "PROPOSED",
                3,
                null,
                null,
                2
        );
        StoryBibleEntry worldRule = entry(
                "world.rule.sealighthouse",
                "rule",
                "海灯不可被凡人触碰",
                "CANON",
                1,
                null,
                null,
                1
        );
        Object service = newApplicationService(List.of(canon, proposal, worldRule));

        List<?> entries = invokeListMethod(service, "listEntriesForChapter", 1001L, 42L);

        assertThat(entries)
                .extracting(entry -> stringProperty(entry, "entryKey"))
                .containsExactly("hero.identity", "world.rule.sealighthouse");
        assertThat(entries)
                .filteredOn(entry -> "hero.identity".equals(stringProperty(entry, "entryKey")))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(stringProperty(entry, "canonicalStatus")).isEqualTo("CANON");
                    assertThat(stringProperty(entry, "content")).isEqualTo("林烬是守夜人见习生");
                });
    }

    @Test
    void UT_APP_STORY_BIBLE_SHOULD_NOT_INFER_SHARED_SECRET_WHEN_CHAPTER_ONLY_SAYS_SUYAN_DOES_NOT_KNOW() {
        Object service = newApplicationService(List.of());
        String chapterText = "第45章，苏砚察觉林烬在隐瞒什么，但她还不知道城主其实是他的生父。林烬转身离开，没有解释。";

        List<?> proposals = invokeListMethod(service,
                "proposeUpdatesFromChapter",
                1001L,
                45L,
                chapterText
        );

        assertThat(proposals)
                .filteredOn(proposal -> "information_boundary".equals(stringProperty(proposal, "entryType")))
                .noneSatisfy(proposal -> assertThat(stringProperty(proposal, "proposedContent"))
                        .contains("林烬与苏砚都知道城主其实是林烬的生父"));
    }

    private StoryBibleEntry entry(String entryKey,
                                  String entryType,
                                  String content,
                                  String canonicalStatus,
                                  Integer riskLevel,
                                  Long validFromChapterId,
                                  Long validToChapterId,
                                  Integer versionNo) {
        StoryBibleEntry entry = new StoryBibleEntry();
        entry.setEntryKey(entryKey);
        entry.setEntryType(entryType);
        entry.setContent(content);
        entry.setCanonicalStatus(canonicalStatus);
        entry.setRiskLevel(riskLevel);
        entry.setValidFromChapterId(validFromChapterId);
        entry.setValidToChapterId(validToChapterId);
        entry.setVersionNo(versionNo);
        return entry;
    }

    private Object newApplicationService(List<StoryBibleEntry> repositoryEntries) {
        StoryBibleRepository repository = mock(StoryBibleRepository.class);
        if (!repositoryEntries.isEmpty()) {
            when(repository.findActiveEntries(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong()))
                    .thenReturn(repositoryEntries);
        }
        return instantiate(APPLICATION_SERVICE, Map.of(StoryBibleRepository.class, repository));
    }

    private Object instantiate(String className, Map<Class<?>, Object> provided) {
        Class<?> type = loadClass(className);
        List<Constructor<?>> constructors = new ArrayList<>(List.of(type.getDeclaredConstructors()));
        constructors.sort((left, right) -> Integer.compare(right.getParameterCount(), left.getParameterCount()));
        for (Constructor<?> constructor : constructors) {
            Object[] args = resolveArguments(constructor.getParameterTypes(), provided);
            if (args == null) {
                continue;
            }
            try {
                constructor.setAccessible(true);
                return constructor.newInstance(args);
            } catch (Exception ex) {
                fail("Failed to instantiate %s via %s: %s".formatted(className, constructor, ex.getMessage()));
            }
        }
        fail("No satisfiable constructor found for %s".formatted(className));
        return null;
    }

    private Object[] resolveArguments(Class<?>[] parameterTypes, Map<Class<?>, Object> provided) {
        Object[] args = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Object direct = findProvided(parameterTypes[i], provided);
            if (direct != null) {
                args[i] = direct;
                continue;
            }
            if (parameterTypes[i].isInterface() || java.lang.reflect.Modifier.isAbstract(parameterTypes[i].getModifiers())) {
                return null;
            }
            if (parameterTypes[i].getName().startsWith("com.penmate.backend.application.storybible.")) {
                args[i] = instantiate(parameterTypes[i].getName(), provided);
                continue;
            }
            return null;
        }
        return args;
    }

    private Object findProvided(Class<?> parameterType, Map<Class<?>, Object> provided) {
        for (Map.Entry<Class<?>, Object> entry : provided.entrySet()) {
            if (parameterType.isAssignableFrom(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            fail("Expected class %s to exist".formatted(className));
            return null;
        }
    }

    private List<?> invokeListMethod(Object target, String methodName, Object... args) {
        if (target == null) {
            fail("Target service must not be null when invoking %s".formatted(methodName));
        }
        Method method = findMethod(target.getClass(), methodName, args.length);
        try {
            method.setAccessible(true);
            Object result = method.invoke(target, args);
            assertThat(result).isInstanceOf(List.class);
            return (List<?>) result;
        } catch (Exception ex) {
            fail("Failed to invoke %s on %s: %s".formatted(methodName, target.getClass().getName(), ex.getMessage()));
            return List.of();
        }
    }

    private Method findMethod(Class<?> type, String methodName, int parameterCount) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        fail("Expected method %s(%s args) on %s".formatted(methodName, parameterCount, type.getName()));
        return null;
    }

    private String stringProperty(Object target, String name) {
        Object value = property(target, name);
        return value == null ? "" : String.valueOf(value);
    }

    private Long longProperty(Object target, String name) {
        Object value = property(target, name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private Integer integerProperty(Object target, String name) {
        Object value = property(target, name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.MIN_VALUE;
    }

    private Object property(Object target, String name) {
        Objects.requireNonNull(target, "target");
        try {
            Method accessor = target.getClass().getMethod(name);
            return accessor.invoke(target);
        } catch (Exception ignored) {
            // ignore and try getter/field
        }
        try {
            Method getter = target.getClass().getMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
            return getter.invoke(target);
        } catch (Exception ignored) {
            // ignore and try field
        }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ex) {
            fail("Expected property %s on %s".formatted(name, target.getClass().getName()));
            return null;
        }
    }
}
