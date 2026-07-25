package com.penmate.backend.application.agent.prompt;

import com.penmate.backend.application.agent.context.ContextPackage;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.StringJoiner;

/** Renders model-visible context data into consistently escaped prompt blocks. */
@Component
public class PromptContextRenderer {

    private final StructuredPromptBlockFormatter blockFormatter;

    public PromptContextRenderer(StructuredPromptBlockFormatter blockFormatter) {
        this.blockFormatter = blockFormatter;
    }

    public String renderEpochCore(ContextPackage contextPackage) {
        return renderListBlock("context type=\"story_bible\" scope=\"epoch_core\"",
                contextPackage.coreStoryBibleEntries());
    }

    public String renderRunContext(ContextPackage contextPackage) {
        StringJoiner blocks = new StringJoiner("\n\n");
        addBlock(blocks, "context type=\"author_profile\" authority=\"preference\"",
                contextPackage.authorProfileSnapshot());
        addBlock(blocks, "context type=\"style\"", contextPackage.styleSnapshot());
        addBlock(blocks, "context type=\"chapter_scope\"", contextPackage.chapterScope());

        LinkedHashSet<String> storyBibleEntries = new LinkedHashSet<>();
        storyBibleEntries.addAll(contextPackage.workingSetEntries());
        storyBibleEntries.addAll(contextPackage.selectedStoryBibleEntries());
        addBlock(blocks, "context type=\"story_bible\" scope=\"run\"", List.copyOf(storyBibleEntries));

        addBlock(blocks, "context type=\"conflict\"", contextPackage.conflicts());
        addBlock(blocks, "context type=\"missing\"", contextPackage.missingContextFlags());
        return blocks.toString();
    }

    public String renderUserRequest(String userRequest) {
        return blockFormatter.wrapBlock("user_request", normalize(userRequest));
    }

    private String renderListBlock(String tagDeclaration, List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return blockFormatter.wrapBlock(tagDeclaration, String.join("\n", values));
    }

    private void addBlock(StringJoiner blocks, String tagDeclaration, List<String> values) {
        String rendered = renderListBlock(tagDeclaration, values);
        if (!rendered.isBlank()) {
            blocks.add(rendered);
        }
    }

    private void addBlock(StringJoiner blocks, String tagDeclaration, String value) {
        String normalized = normalize(value);
        if (!normalized.isEmpty()) {
            blocks.add(blockFormatter.wrapBlock(tagDeclaration, normalized));
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
