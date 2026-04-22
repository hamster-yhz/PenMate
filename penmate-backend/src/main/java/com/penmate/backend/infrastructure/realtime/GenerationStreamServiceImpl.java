package com.penmate.backend.infrastructure.realtime;

import com.penmate.backend.domain.shared.service.GenerationStreamService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 生成任务流式输出服务实现。
 * <p>封装 SSE 连接创建逻辑，为生成任务提供统一的流式通道入口。</p>
 */
@Service
public class GenerationStreamServiceImpl implements GenerationStreamService {

    private final GenerationSseEmitterHub generationSseEmitterHub;

    public GenerationStreamServiceImpl(GenerationSseEmitterHub generationSseEmitterHub) {
        this.generationSseEmitterHub = generationSseEmitterHub;
    }

    /**
     * 打开指定任务的 SSE 通道。
     * <p>流程：委托 {@link GenerationSseEmitterHub} 创建并注册 emitter，供后续事件推送。</p>
     */
    @Override
    public SseEmitter openStream(Long taskId) {
        return generationSseEmitterHub.create(taskId);
    }
}

