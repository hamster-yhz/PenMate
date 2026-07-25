package com.penmate.backend.infrastructure.importing;

import com.penmate.backend.application.novel.importing.NovelImportSourceParser;
import com.penmate.backend.domain.novel.importing.NovelImportDraft;
import com.penmate.backend.domain.novel.importing.NovelImportFormat;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class TxtNovelImportSourceParser implements NovelImportSourceParser {
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final Pattern VOLUME = Pattern.compile(
            "^(?:第[0-9零〇一二两三四五六七八九十百千万]+卷|卷[0-9零〇一二两三四五六七八九十百千万]+)(?:[\\s:：.-]+.*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAPTER = Pattern.compile(
            "^(?:第[0-9零〇一二两三四五六七八九十百千万]+(?:章|节|回)|(?:chapter|chap\\.)\\s*\\d+)(?:[\\s:：.-]+.*)?$",
            Pattern.CASE_INSENSITIVE);

    @Override public NovelImportFormat format() { return NovelImportFormat.TXT; }

    @Override
    public NovelImportDraft parse(String filename, InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length == 0) throw new IllegalArgumentException("TXT file is empty");
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("TXT file exceeds 20 MB");
        String text = decode(bytes).replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        if (text.isBlank()) throw new IllegalArgumentException("TXT file contains no text");

        String title = titleFromFilename(filename, ".txt");
        List<String> lines = Arrays.asList(text.split("\n", -1));
        int firstStructure = firstStructure(lines);
        StructuredTextDraftBuilder builder = new StructuredTextDraftBuilder(format());
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String heading = line.strip();
            if (index < firstStructure && (heading.isBlank() || heading.equals(title))) continue;
            if (VOLUME.matcher(heading).matches()) builder.volume(heading);
            else if (CHAPTER.matcher(heading).matches()) builder.chapter(heading);
            else builder.content(line);
        }
        return builder.build(title);
    }

    static String titleFromFilename(String filename, String extension) {
        String value = filename == null ? "" : filename.strip();
        value = value.replaceFirst("(?i)" + Pattern.quote(extension) + "$", "");
        if (value.isBlank()) value = "导入作品";
        return value.length() > 200 ? value.substring(0, 200) : value;
    }

    private int firstStructure(List<String> lines) {
        for (int index = 0; index < lines.size(); index++) {
            String value = lines.get(index).strip();
            if (VOLUME.matcher(value).matches() || CHAPTER.matcher(value).matches()) return index;
        }
        return -1;
    }

    private String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("TXT file must use UTF-8 encoding", exception);
        }
    }
}
