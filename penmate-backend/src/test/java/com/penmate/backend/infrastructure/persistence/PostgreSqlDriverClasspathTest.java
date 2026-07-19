package com.penmate.backend.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PostgreSqlDriverClasspathTest {

    @Test
    void postgreSqlDriverIsAvailableAtRuntime() {
        assertDoesNotThrow(() -> Class.forName("org.postgresql.Driver"));
    }
}
