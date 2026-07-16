package com.penmate.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(packages = "com.penmate.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class StoryBibleContextEpochArchitectureTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final List<String> FORBIDDEN_PRODUCTION_SYMBOLS = List.of(
            "StoryBibleVersion",
            "StoryBibleVersionSelector",
            "AgentPreflightCoordinator",
            "AgentPreflightDecision",
            "DefaultAgentPreflightCoordinator",
            "AgentRouteDecision",
            "includeStoryBibleContext",
            "preflight",
            "skill_prompt_read"
    );

    @ArchTest
    static final ArchRule story_bible_domain_should_not_depend_on_agent_application =
            noClasses()
                    .that().resideInAPackage("..domain.storybible..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..application.agent..");

    @Test
    void should_remove_legacy_story_bible_and_full_preflight_symbols_from_production() throws IOException {
        try (var paths = Files.walk(MAIN_JAVA)) {
            List<Path> javaFiles = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();

            for (String forbiddenSymbol : FORBIDDEN_PRODUCTION_SYMBOLS) {
                assertThat(javaFiles)
                        .filteredOn(path -> contains(path, forbiddenSymbol))
                        .as("production source containing forbidden symbol %s", forbiddenSymbol)
                        .isEmpty();
            }
        }
    }

    private boolean contains(Path path, String value) {
        try {
            return Files.readString(path).contains(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect " + path, ex);
        }
    }
}
