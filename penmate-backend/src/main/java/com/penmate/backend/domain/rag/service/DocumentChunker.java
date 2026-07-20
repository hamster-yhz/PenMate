package com.penmate.backend.domain.rag.service;

import java.util.ArrayList;
import java.util.List;

public class DocumentChunker {
    private final int maxChunks;

    public DocumentChunker(int maxChunks) {
        this.maxChunks = maxChunks;
    }

    public List<String> chunk(String text, int target, int overlap, int hardMax) {
        if (text == null || text.isBlank()) return List.of();
        if (target <= 0 || overlap < 0 || overlap >= target || hardMax < target) {
            throw new IllegalArgumentException("Invalid chunk configuration");
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int desiredEnd = Math.min(start + target, normalized.length());
            int end = findBoundary(normalized, start, desiredEnd, Math.min(start + hardMax, normalized.length()));
            String value = normalized.substring(start, end).trim();
            if (!value.isEmpty()) chunks.add(value);
            if (chunks.size() > maxChunks) {
                throw new IllegalArgumentException("Document exceeds the maximum chunk count");
            }
            if (end >= normalized.length()) break;
            int next = Math.max(start + 1, end - overlap);
            start = skipWhitespace(normalized, next);
        }
        return List.copyOf(chunks);
    }

    private int findBoundary(String text, int start, int targetEnd, int hardEnd) {
        if (hardEnd >= text.length()) return text.length();
        int minimum = Math.min(targetEnd, start + Math.max(1, (targetEnd - start) * 3 / 5));
        int boundary = lastBoundary(text, start, targetEnd, minimum);
        if (boundary > start) return boundary;
        boundary = nextBoundary(text, targetEnd, hardEnd);
        return boundary > start ? boundary : hardEnd;
    }

    private int lastBoundary(String text, int start, int end, int minimum) {
        String[] markers = {"\n#", "\n\n", "\n", "。", "！", "？", ". ", "! ", "? ", "；", "; "};
        int best = -1;
        for (String marker : markers) {
            int candidate = text.lastIndexOf(marker, end);
            if (candidate >= Math.max(start, minimum)) best = Math.max(best, candidate + marker.length());
        }
        return best;
    }

    private int nextBoundary(String text, int start, int hardEnd) {
        String markers = "\n。！？.!?；;";
        for (int i = start; i < hardEnd; i++) {
            if (markers.indexOf(text.charAt(i)) >= 0) return i + 1;
        }
        return hardEnd;
    }

    private int skipWhitespace(String text, int start) {
        int result = start;
        while (result < text.length() && Character.isWhitespace(text.charAt(result))) result++;
        return result;
    }
}
