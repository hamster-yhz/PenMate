package com.penmate.backend.domain.shared.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface GenerationStreamService {

    SseEmitter openStream(Long taskId);
}

