package com.penmate.backend.domain.shared.service;

/**
 * 业务语义 ID 生成器。
 * <p>统一为用户、项目、章节等业务身份字段生成雪花 ID。</p>
 */
public interface BusinessIdGenerator {

    /**
     * 生成下一个业务 ID。
     *
     * @return 雪花算法生成的 Long 型业务 ID
     */
    Long nextId();
}
