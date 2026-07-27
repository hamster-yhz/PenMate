package com.penmate.backend.application.agent.orchestration;

import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.AgentSessionContextSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ConversationWindowBuilder {

    private final AgentRepository agentRepository;
    private final AgentSessionContextSummaryRepository contextSummaries;

    public List<AgentLlmMessage> build(Long conversationId, String currentPrompt, Integer contextWindowTurns) {
        if (conversationId == null || contextWindowTurns == null || contextWindowTurns <= 0) {
            return List.of();
        }

        return buildWithSummary(conversationId, agentRepository.listMessages(conversationId), currentPrompt, contextWindowTurns);
    }

    public List<AgentLlmMessage> buildBeforeTurn(Long conversationId, Long turnId, Integer contextWindowTurns) {
        if (conversationId == null || turnId == null || contextWindowTurns == null || contextWindowTurns <= 0) {
            return List.of();
        }

        return buildWithSummary(conversationId, agentRepository.listMessagesBeforeTurn(conversationId, turnId), null, contextWindowTurns);
    }

    private List<AgentLlmMessage> buildWithSummary(Long conversationId, List<AgentMessage> messages,
                                                   String currentPrompt, int contextWindowTurns) {
        var summary = contextSummaries == null ? null : contextSummaries.find(conversationId);
        int cutoff = summary == null || summary.cutoffMessageSeq() == null ? 0 : summary.cutoffMessageSeq();
        List<AgentMessage> tail = (messages == null ? List.<AgentMessage>of() : messages).stream()
                .filter(message -> message != null && message.getSeqNo() != null && message.getSeqNo() > cutoff)
                .toList();
        List<AgentLlmMessage> window = new ArrayList<>();
        if (summary != null && summary.summaryJson() != null && !summary.summaryJson().isBlank()) {
            window.add(AgentLlmMessage.system("Earlier conversation context (compressed):\n" + summary.summaryJson()));
        }
        window.addAll(build(tail, currentPrompt, contextWindowTurns));
        return List.copyOf(window);
    }

    private List<AgentLlmMessage> build(List<AgentMessage> messages, String currentPrompt, int contextWindowTurns) {
        List<AgentMessage> sortedMessages = (messages == null ? List.<AgentMessage>of() : messages).stream()
                .filter(this::isUsableConversationMessage)
                .sorted(Comparator.comparing(AgentMessage::getSeqNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(AgentMessage::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        List<AgentMessage> historyOnly = dropCurrentPromptTail(sortedMessages, currentPrompt);
        List<List<AgentMessage>> turns = groupIntoTurns(historyOnly);
        if (turns.isEmpty()) {
            return List.of();
        }

        int fromIndex = Math.max(0, turns.size() - contextWindowTurns);
        List<AgentLlmMessage> result = new ArrayList<>();
        for (List<AgentMessage> turn : turns.subList(fromIndex, turns.size())) {
            for (AgentMessage message : turn) {
                result.add(toLlmMessage(message));
            }
        }
        return List.copyOf(result);
    }

    private boolean isUsableConversationMessage(AgentMessage message) {
        if (message == null || message.getRole() == null || message.getContentMd() == null) {
            return false;
        }
        String role = message.getRole().trim().toLowerCase(Locale.ROOT);
        return ("user".equals(role) || "assistant".equals(role)) && !message.getContentMd().isBlank();
    }

    private List<AgentMessage> dropCurrentPromptTail(List<AgentMessage> messages, String currentPrompt) {
        if (messages.isEmpty() || currentPrompt == null || currentPrompt.isBlank()) {
            return messages;
        }
        AgentMessage tail = messages.get(messages.size() - 1);
        if ("user".equalsIgnoreCase(tail.getRole())
                && currentPrompt.trim().equals(tail.getContentMd() == null ? null : tail.getContentMd().trim())) {
            return messages.subList(0, messages.size() - 1);
        }
        return messages;
    }

    private List<List<AgentMessage>> groupIntoTurns(List<AgentMessage> messages) {
        List<AgentMessage> users = new ArrayList<>();
        List<AgentMessage> assistants = new ArrayList<>();
        for (AgentMessage message : messages) {
            if ("user".equalsIgnoreCase(message.getRole())) {
                users.add(message);
                continue;
            }
            if ("assistant".equalsIgnoreCase(message.getRole())) {
                assistants.add(message);
            }
        }

        int completedTurnCount = Math.min(users.size(), assistants.size());
        if (completedTurnCount <= 0) {
            return List.of();
        }

        List<List<AgentMessage>> turns = new ArrayList<>();
        int assistantIndex = 0;
        for (int i = 0; i < completedTurnCount; i++) {
            List<AgentMessage> turn = new ArrayList<>();
            turn.add(users.get(i));

            int remainingTurns = completedTurnCount - i - 1;
            int assistantLimitExclusive = assistants.size() - remainingTurns;
            while (assistantIndex < assistantLimitExclusive) {
                turn.add(assistants.get(assistantIndex));
                assistantIndex++;
            }
            turns.add(List.copyOf(turn));
        }
        return turns;
    }

    private AgentLlmMessage toLlmMessage(AgentMessage message) {
        return "assistant".equalsIgnoreCase(message.getRole())
                ? AgentLlmMessage.assistant(message.getContentMd(), List.of())
                : AgentLlmMessage.user(message.getContentMd());
    }
}
