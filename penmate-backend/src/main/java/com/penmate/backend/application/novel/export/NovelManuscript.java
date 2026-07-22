package com.penmate.backend.application.novel.export;

import java.util.List;

public record NovelManuscript(String title, List<Volume> volumes) {

    public NovelManuscript {
        title = normalize(title, "Untitled novel");
        volumes = volumes == null ? List.of() : List.copyOf(volumes);
    }

    public record Volume(String title, List<Chapter> chapters) {
        public Volume {
            title = normalize(title, "Untitled volume");
            chapters = chapters == null ? List.of() : List.copyOf(chapters);
        }
    }

    public record Chapter(String title, String content) {
        public Chapter {
            title = normalize(title, "Untitled chapter");
            content = content == null ? "" : content;
        }
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
