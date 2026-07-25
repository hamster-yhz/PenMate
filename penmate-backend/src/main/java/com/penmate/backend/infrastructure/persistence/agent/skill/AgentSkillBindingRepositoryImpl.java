package com.penmate.backend.infrastructure.persistence.agent.skill;

import com.penmate.backend.application.agent.skill.AgentRunSkillBinding;
import com.penmate.backend.application.agent.skill.AgentSkillBindingRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentSkillBindingRepositoryImpl implements AgentSkillBindingRepository {

    private final AgentSkillBindingMapper mapper;

    public AgentSkillBindingRepositoryImpl(AgentSkillBindingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<String> listSessionSkillNames(Long sessionId) {
        return List.copyOf(mapper.listSessionSkillNames(sessionId));
    }

    @Override
    public void replaceSessionSkillNames(Long sessionId, List<String> skillNames) {
        mapper.deleteSessionBindings(sessionId);
        for (String skillName : skillNames) {
            if (mapper.insertSessionBinding(sessionId, skillName) != 1) {
                throw new IllegalStateException("Failed to persist session Skill binding: " + skillName);
            }
        }
    }

    @Override
    public void saveSnapshot(String contentHash, String content) {
        mapper.saveSnapshot(contentHash, content);
    }

    @Override
    public AgentRunSkillBinding findRunBinding(Long runId, String skillName) {
        return mapper.findRunBinding(runId, skillName);
    }

    @Override
    public List<AgentRunSkillBinding> listRunBindings(Long runId) {
        return List.copyOf(mapper.listRunBindings(runId));
    }

    @Override
    public int countRunBindings(Long runId) {
        return mapper.countRunBindings(runId);
    }

    @Override
    public int insertRunBinding(Long runId, String skillName, String contentHash,
                                String activationSource, String toolCallId) {
        return mapper.insertRunBinding(runId, skillName, contentHash, activationSource, toolCallId);
    }
}
