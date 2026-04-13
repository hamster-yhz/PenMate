package com.penmate.backend.infrastructure.realtime;

import com.penmate.backend.domain.shared.service.GenerationStreamService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class GenerationStreamServiceImpl implements GenerationStreamService {

    private final GenerationSseEmitterHub generationSseEmitterHub;

    public GenerationStreamServiceImpl(GenerationSseEmitterHub generationSseEmitterHub) {
        this.generationSseEmitterHub = generationSseEmitterHub;
    }

    @Override
    public SseEmitter openStream(Long taskId) {
        return generationSseEmitterHub.create(taskId);
    }
}

