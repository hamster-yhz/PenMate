package com.penmate.backend.interfaces.api.agent.dto;

import com.penmate.backend.application.agent.context.StoryBibleRoutingMode;

public final class StoryBibleRoutingPreferenceDto {
    private StoryBibleRoutingPreferenceDto() {
    }

    public record Update(StoryBibleRoutingMode mode, String routerModelConfigId) {
    }

    public record View(StoryBibleRoutingMode mode, String routerModelConfigId,
                       Long routerModelConfigRevision, boolean inherited) {
    }
}
