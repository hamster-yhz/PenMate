package com.penmate.backend.infrastructure.agent.prompt;

import com.penmate.backend.application.agent.prompt.SystemPromptBundle;
import com.penmate.backend.application.agent.prompt.SystemPromptDocument;
import com.penmate.backend.application.agent.prompt.SystemPromptProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

@Component
public class ClasspathMarkdownSystemPromptProvider implements SystemPromptProvider {

    private static final String PROMPT_ROOT = "prompts/agent/system";
    private static final String BUNDLE_MANIFEST = "bundle.properties";
    private static final String DOCUMENTS_PROPERTY = "documents";

    @Override
    public SystemPromptBundle loadBundle(String stage) {
        String bundlePath = PROMPT_ROOT + "/" + stage + "/default";
        List<SystemPromptDocument> documents = loadDocuments(stage, bundlePath);
        String assembledPrompt = documents.stream()
                .map(SystemPromptDocument::content)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
        return new SystemPromptBundle(stage, List.copyOf(documents), assembledPrompt);
    }

    private List<SystemPromptDocument> loadDocuments(String stage, String bundlePath) {
        String manifestPath = bundlePath + "/" + BUNDLE_MANIFEST;
        ClassPathResource manifest = new ClassPathResource(manifestPath);
        if (manifest.exists()) {
            return loadManifestDocuments(bundlePath, manifestPath, manifest);
        }
        if (!"skills".equals(stage)) {
            throw new IllegalArgumentException("Prompt bundle manifest does not exist: " + manifestPath);
        }
        return scanMarkdownDocuments(bundlePath);
    }

    private List<SystemPromptDocument> loadManifestDocuments(String bundlePath,
                                                              String manifestPath,
                                                              ClassPathResource manifest) {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(manifest.getInputStream(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load prompt bundle manifest: " + manifestPath, e);
        }

        String configuredDocuments = properties.getProperty(DOCUMENTS_PROPERTY, "").trim();
        if (configuredDocuments.isEmpty()) {
            throw new IllegalArgumentException(
                    "Prompt bundle manifest must declare documents: " + manifestPath);
        }

        LinkedHashSet<String> documentPaths = new LinkedHashSet<>();
        for (String configuredPath : configuredDocuments.split(",")) {
            String documentPath = configuredPath.trim().replace('\\', '/');
            if (documentPath.isEmpty() || documentPath.startsWith("/") || documentPath.contains("..")) {
                throw new IllegalArgumentException(
                        "Prompt bundle manifest contains an invalid document path: " + manifestPath);
            }
            if (!documentPaths.add(documentPath)) {
                throw new IllegalArgumentException(
                        "Prompt bundle manifest contains a duplicate document path: " + documentPath);
            }
        }

        List<SystemPromptDocument> documents = documentPaths.stream()
                .map(documentPath -> loadManifestDocument(bundlePath, manifestPath, documentPath))
                .toList();
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("No prompt documents declared by manifest: " + manifestPath);
        }
        return documents;
    }

    private SystemPromptDocument loadManifestDocument(String bundlePath,
                                                       String manifestPath,
                                                       String documentPath) {
        if (!documentPath.endsWith(".md")) {
            throw new IllegalArgumentException(
                    "Prompt bundle manifest documents must be markdown files: " + manifestPath);
        }
        String resourcePath = PROMPT_ROOT + "/" + documentPath;
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalArgumentException(
                    "Prompt bundle manifest references a missing document: " + resourcePath);
        }
        try {
            String content = resource.getContentAsString(StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                throw new IllegalArgumentException(
                        "Prompt bundle manifest references a blank document: " + resourcePath);
            }
            String fileName = Path.of(documentPath).getFileName().toString();
            return new SystemPromptDocument(fileName, resourcePath, content);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to read markdown prompt document for bundle " + bundlePath + ": " + resourcePath, e);
        }
    }

    private List<SystemPromptDocument> scanMarkdownDocuments(String bundlePath) {
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
