package com.penmate.backend.infrastructure.importing;

import com.penmate.backend.application.novel.importing.NovelImportSourceParser;
import com.penmate.backend.domain.novel.importing.NovelImportDraft;
import com.penmate.backend.domain.novel.importing.NovelImportFormat;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MarkdownNovelImportSourceParser implements NovelImportSourceParser {
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");

    @Override public NovelImportFormat format() { return NovelImportFormat.MARKDOWN; }

    @Override
    public NovelImportDraft parse(String filename, InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length == 0) throw new IllegalArgumentException("Markdown file is empty");
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Markdown file exceeds 20 MB");
        String text = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = Arrays.asList(text.split("\n", -1));
        boolean hasLevelThree = lines.stream().map(String::strip).anyMatch(line -> line.startsWith("### "));
        String projectTitle = TxtNovelImportSourceParser.titleFromFilename(filename,
                filename != null && filename.toLowerCase().endsWith(".markdown") ? ".markdown" : ".md");
        StructuredTextDraftBuilder builder = new StructuredTextDraftBuilder(format());
        boolean projectHeadingConsumed = false;
        boolean inFence = false;
        for (String line : lines) {
            String stripped = line.strip();
            if (stripped.startsWith("```") || stripped.startsWith("~~~")) inFence = !inFence;
            Matcher heading = inFence ? null : HEADING.matcher(stripped);
            if (heading != null && heading.matches()) {
                int level = heading.group(1).length();
                String title = heading.group(2).strip();
                if (level == 1 && !projectHeadingConsumed) {
                    projectTitle = title;
                    projectHeadingConsumed = true;
                } else if (hasLevelThree && level == 2) {
                    builder.volume(title);
                } else if ((hasLevelThree && level >= 3) || (!hasLevelThree && level >= 2)) {
                    builder.chapter(title);
                } else {
                    builder.content(line);
                }
            } else {
                builder.content(line);
            }
        }
        return builder.build(projectTitle);
    }
}
