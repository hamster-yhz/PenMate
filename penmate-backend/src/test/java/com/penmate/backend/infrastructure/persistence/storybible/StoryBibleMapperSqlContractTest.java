package com.penmate.backend.infrastructure.persistence.storybible;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class StoryBibleMapperSqlContractTest {

    @Test
    void node_type_queries_use_a_non_reserved_system_alias() throws Exception {
        assertSystemAliasIsQuoted(method("findNodeTypes", Long.class));
        assertSystemAliasIsQuoted(method("findNodeType", Long.class, Long.class));
    }

    private Method method(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return StoryBibleMapper.class.getMethod(name, parameterTypes);
    }

    private void assertSystemAliasIsQuoted(Method method) {
        String sql = String.join("\n", method.getAnnotation(Select.class).value());
        assertThat(sql)
                .contains("is_system AS \"system\"")
                .doesNotContain("is_system AS system");
    }
}
