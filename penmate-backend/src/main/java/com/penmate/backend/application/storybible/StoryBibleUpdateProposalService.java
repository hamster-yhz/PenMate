package com.penmate.backend.application.storybible;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StoryBibleUpdateProposalService {

    public List<StoryBibleProposalItem> proposeUpdatesFromChapter(Long projectId, Long chapterId, String chapterText) {
        String normalizedText = normalize(chapterText);
        if (normalizedText.isEmpty()) {
            return List.of();
        }
        List<StoryBibleProposalItem> proposals = new ArrayList<>();
        if (containsAny(normalizedText, "林烬", "白檀", "苏砚")) {
            proposals.add(new StoryBibleProposalItem(
                    "character.scene.participants",
                    "character",
                    buildCharacterContent(normalizedText),
                    "PROPOSED",
                    1,
                    firstMatchingSentence(normalizedText, "林烬", "白檀", "苏砚"),
                    chapterId,
                    "DIRECT_TEXT"
            ));
        }
        String locationSentence = firstMatchingSentence(normalizedText, "雾港钟楼", "钟楼");
        if (!locationSentence.isEmpty()) {
            proposals.add(new StoryBibleProposalItem(
                    "location.scene.primary",
                    "location",
                    locationSentence.contains("雾港钟楼") ? "当前场景主地点为雾港钟楼。" : "当前场景主地点为钟楼。",
                    "PROPOSED",
                    1,
                    locationSentence,
                    chapterId,
                    "DIRECT_TEXT"
            ));
        }
        String eventSentence = firstMatchingSentence(normalizedText, "交手", "打碎", "坦白");
        if (!eventSentence.isEmpty()) {
            proposals.add(new StoryBibleProposalItem(
                    "event.scene.change",
                    "event",
                    eventSentence,
                    "PROPOSED",
                    2,
                    eventSentence,
                    chapterId,
                    "DIRECT_TEXT"
            ));
        }
        StoryBibleProposalItem informationBoundary = buildInformationBoundaryProposal(chapterId, normalizedText);
        if (informationBoundary != null) {
            proposals.add(informationBoundary);
        }
        return List.copyOf(proposals);
    }

    private StoryBibleProposalItem buildInformationBoundaryProposal(Long chapterId, String chapterText) {
        String privateKnowledgeSentence = firstMatchingSentence(chapterText, "只有林烬知道");
        if (!privateKnowledgeSentence.isEmpty()) {
            return new StoryBibleProposalItem(
                    "information_boundary.linjin.secret",
                    "information_boundary",
                    privateKnowledgeSentence,
                    "PROPOSED",
                    3,
                    privateKnowledgeSentence,
                    chapterId,
                    "BOUNDARY_INFERRED"
            );
        }
        String disclosedKnowledgeSentence = firstMatchingSentence(chapterText, "坦白", "不再只属于", "告诉了苏砚", "告知了苏砚");
        if (!disclosedKnowledgeSentence.isEmpty()) {
            return new StoryBibleProposalItem(
                    "information_boundary.linjin.secret",
                    "information_boundary",
                    "林烬与苏砚都知道城主其实是林烬的生父。",
                    "PROPOSED",
                    3,
                    disclosedKnowledgeSentence,
                    chapterId,
                    "BOUNDARY_UPDATE"
            );
        }
        return null;
    }

    private String buildCharacterContent(String chapterText) {
        List<String> names = new ArrayList<>();
        if (chapterText.contains("林烬")) {
            names.add("林烬");
        }
        if (chapterText.contains("白檀")) {
            names.add("白檀");
        }
        if (chapterText.contains("苏砚")) {
            names.add("苏砚");
        }
        if (names.isEmpty()) {
            return "本章出现了新的关键角色。";
        }
        return "本章关键角色包括：" + String.join("、", names) + "。";
    }

    private String firstMatchingSentence(String chapterText, String... markers) {
        String[] sentences = chapterText.split("[。！？]");
        for (String sentence : sentences) {
            String normalized = sentence.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            for (String marker : markers) {
                if (normalized.contains(marker)) {
                    return normalized;
                }
            }
        }
        return "";
    }

    private boolean containsAny(String text, String... markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
