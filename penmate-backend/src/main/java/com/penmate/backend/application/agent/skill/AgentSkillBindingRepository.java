package com.penmate.backend.application.agent.skill;

import java.util.List;

public interface AgentSkillBindingRepository {

    List<String> listSessionSkillNames(Long sessionId);

    void replaceSessionSkillNames(Long sessionId, List<String> skillNames);

    void saveSnapshot(String contentHash, String content);

    AgentRunSkillBinding findRunBinding(Long runId, String skillName);

    List<AgentRunSkillBinding> listRunBindings(Long runId);

    int countRunBindings(Long runId);

    int insertRunBinding(Long runId, String skillName, String contentHash,
                         String activationSource, String toolCallId);
}
