package com.penmate.backend.application.storybible;

import com.penmate.backend.application.support.BaseApplicationServiceTest;
import com.penmate.backend.domain.storybible.model.StoryBibleEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@ExtendWith(MockitoExtension.class)
class StoryBibleVersionSelectorTest extends BaseApplicationServiceTest {

    private static final String VERSION_SELECTOR = "com.penmate.backend.application.storybible.StoryBibleVersionSelector";

    @Test
    void UT_APP_STORY_BIBLE_VERSION_SELECTOR_SHOULD_RETURN_DIFFERENT_SECRET_KNOWLEDGE_VERSIONS_FOR_CHAPTER_42_AND_45() {
        Object selector = instantiate(VERSION_SELECTOR);
        StoryBibleEntry secretAtChapter42 = entry(
                "character.secret.knowledge.linjin",
                "character",
                "只有林烬知道城主其实是他的生父",
                "CANON",
                1,
                42L,
                44L,
                1
        );
        StoryBibleEntry secretAtChapter45 = entry(
                "character.secret.knowledge.linjin",
                "character",
                "林烬与苏砚都知道城主其实是林烬的生父",
                "CANON",
                1,
                45L,
                null,
                2
        );
        StoryBibleEntry stableIdentity = entry(
                "hero.identity",
                "character",
                "林烬是守夜人见习生",
                "CANON",
                1,
                null,
                null,
                1
        );

        List<StoryBibleEntry> chapter42 = invokeSelector(selector, List.of(secretAtChapter42, secretAtChapter45, stableIdentity), 42L);
        List<StoryBibleEntry> chapter45 = invokeSelector(selector, List.of(secretAtChapter42, secretAtChapter45, stableIdentity), 45L);

        assertThat(chapter42)
                .filteredOn(entry -> "character.secret.knowledge.linjin".equals(entry.getEntryKey()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getContent()).contains("只有林烬知道");
                    assertThat(entry.getVersionNo()).isEqualTo(1);
                });
        assertThat(chapter45)
                .filteredOn(entry -> "character.secret.knowledge.linjin".equals(entry.getEntryKey()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getContent()).contains("林烬与苏砚都知道");
                    assertThat(entry.getVersionNo()).isEqualTo(2);
                });
        assertThat(chapter42)
                .filteredOn(entry -> "hero.identity".equals(entry.getEntryKey()))
                .singleElement()
                .satisfies(entry -> assertThat(entry.getContent()).isEqualTo("林烬是守夜人见习生"));
        assertThat(chapter45)
                .filteredOn(entry -> "hero.identity".equals(entry.getEntryKey()))
                .singleElement()
                .satisfies(entry -> assertThat(entry.getContent()).isEqualTo("林烬是守夜人见习生"));
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

    private Object instantiate(String className) {
        try {
            Class<?> type = Class.forName(className);
            List<Constructor<?>> constructors = new ArrayList<>(List.of(type.getDeclaredConstructors()));
            constructors.sort(Comparator.comparingInt(Constructor::getParameterCount));
            for (Constructor<?> constructor : constructors) {
                if (constructor.getParameterCount() == 0) {
                    constructor.setAccessible(true);
                    return constructor.newInstance();
                }
            }
            fail("Expected no-args constructor for %s".formatted(className));
            return null;
        } catch (ClassNotFoundException ex) {
            fail("Expected class %s to exist".formatted(className));
            return null;
        } catch (Exception ex) {
            fail("Failed to instantiate %s: %s".formatted(className, ex.getMessage()));
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<StoryBibleEntry> invokeSelector(Object selector, List<StoryBibleEntry> entries, Long chapterId) {
        Method method = findMethod(selector.getClass(), "selectForChapter", 2);
        try {
            Object result = method.invoke(selector, entries, chapterId);
            assertThat(result).isInstanceOf(List.class);
            return (List<StoryBibleEntry>) result;
        } catch (Exception ex) {
            fail("Failed to invoke selector: %s".formatted(ex.getMessage()));
            return List.of();
        }
    }

    private Method findMethod(Class<?> type, String methodName, int parameterCount) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                method.setAccessible(true);
                return method;
            }
        }
        fail("Expected method %s(%s args) on %s".formatted(methodName, parameterCount, type.getName()));
        return null;
    }
}
