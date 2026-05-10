package com.penmate.backend.infrastructure.agent.prompt;

import com.penmate.backend.application.agent.prompt.SystemPromptBundle;
import com.penmate.backend.application.agent.prompt.SystemPromptDocument;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class ClasspathMarkdownSystemPromptProvider implements SystemPromptProvider {

    private static final String PROMPT_ROOT = "prompts/agent/system";

    @Override
    public SystemPromptBundle loadBundle(String stage, String profile) {
        String bundlePath = PROMPT_ROOT + "/" + stage + "/" + profile;
        List<SystemPromptDocument> documents = loadDocuments(bundlePath);
        String assembledPrompt = documents.stream()
                .map(SystemPromptDocument::content)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
        return new SystemPromptBundle(stage, profile, List.copyOf(documents), assembledPrompt);
    }

    private List<SystemPromptDocument> loadDocuments(String bundlePath) {
        try {
            Path directory = resolveDirectory(bundlePath);
            try (Stream<Path> paths = Files.list(directory)) {
                List<SystemPromptDocument> documents = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".md"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .map(path -> toDocument(bundlePath, path))
                        .toList();
                if (documents.isEmpty()) {
                    throw new IllegalArgumentException("No markdown prompt documents found under classpath directory: " + bundlePath);
                }
                return documents;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load markdown prompt bundle from classpath: " + bundlePath, e);
        }
    }

    private Path resolveDirectory(String bundlePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(bundlePath);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Prompt bundle directory does not exist: " + bundlePath);
        }
        URI uri = resource.getURI();
        if ("jar".equalsIgnoreCase(uri.getScheme())) {
            FileSystem fileSystem = getOrCreateFileSystem(uri);
            return fileSystem.getPath(bundlePath);
        }
        return Path.of(uri);
    }

    private FileSystem getOrCreateFileSystem(URI uri) throws IOException {
        try {
            return FileSystems.getFileSystem(uri);
        } catch (Exception ignored) {
            return FileSystems.newFileSystem(uri, Map.of());
        }
    }

    private SystemPromptDocument toDocument(String bundlePath, Path path) {
        try {
            String fileName = path.getFileName().toString();
            String content = Files.readString(path, StandardCharsets.UTF_8).trim();
            return new SystemPromptDocument(fileName, bundlePath + "/" + fileName, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read markdown prompt document: " + path, e);
        }
    }
}
