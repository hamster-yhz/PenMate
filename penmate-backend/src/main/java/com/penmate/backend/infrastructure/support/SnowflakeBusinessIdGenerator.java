package com.penmate.backend.infrastructure.support;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.lang.Snowflake;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.springframework.stereotype.Component;

/**
 * 基于 Hutool Snowflake 的业务 ID 生成器实现。
 */
@Component
public class SnowflakeBusinessIdGenerator implements BusinessIdGenerator {

    private final Snowflake snowflake;

    public SnowflakeBusinessIdGenerator() {
        this.snowflake = IdUtil.getSnowflake();
    }

    @Override
    public Long nextId() {
        return snowflake.nextId();
    }
}
