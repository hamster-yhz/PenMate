package com.penmate.backend.application.novel;

import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class ManuscriptPositionResolver {

    public static final String UNRESOLVED_ANCHOR = "MANUSCRIPT_ANCHOR_UNRESOLVED";

    private final NovelGateway novelGateway;

    public ManuscriptPositionResolver(NovelGateway novelGateway) {
        this.novelGateway = Objects.requireNonNull(novelGateway, "novelGateway");
    }

    public Resolution resolve(Long projectId, Long chapterId) {
        if (projectId == null || chapterId == null) {
            return Resolution.unresolved(chapterId);
        }
        List<NovelChapter> ordered = novelGateway.findChaptersByProjectId(projectId);
        for (int index = 0; index < ordered.size(); index++) {
            NovelChapter chapter = ordered.get(index);
            if (Objects.equals(chapterId, chapter.getChapterId())) {
                return Resolution.resolved(chapterId, index, index + 1);
            }
        }
        return Resolution.unresolved(chapterId);
    }

    public record Resolution(
            boolean resolved,
            Long chapterId,
            Integer ordinal,
            Integer displayNo,
            String conflictCode
    ) {
        public static Resolution resolved(Long chapterId, int ordinal, int displayNo) {
            return new Resolution(true, chapterId, ordinal, displayNo, null);
        }

        public static Resolution unresolved(Long chapterId) {
            return new Resolution(false, chapterId, null, null, UNRESOLVED_ANCHOR);
        }
    }
}
