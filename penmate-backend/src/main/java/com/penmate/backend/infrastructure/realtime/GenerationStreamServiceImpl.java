package com.penmate.backend.infrastructure.realtime;

import com.penmate.backend.domain.shared.service.GenerationStreamService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * GenerationStreamServiceImpl。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Service
public class GenerationStreamServiceImpl implements GenerationStreamService {

    private final GenerationSseEmitterHub generationSseEmitterHub;

    public GenerationStreamServiceImpl(GenerationSseEmitterHub generationSseEmitterHub) {
        this.generationSseEmitterHub = generationSseEmitterHub;
    }

    /**
     * 处理业务请求。
     *
     * @param taskId 入参：taskId
     * @return 出参：处理结果
     */
    @Override
    public SseEmitter openStream(Long taskId) {
        return generationSseEmitterHub.create(taskId);
    }
}

