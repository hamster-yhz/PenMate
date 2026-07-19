package com.penmate.backend.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MysqlDriverClasspathTest {

    @Test
    void mysqlDriverIsAvailableAtRuntime() {
        assertDoesNotThrow(() -> Class.forName("com.mysql.cj.jdbc.Driver"));
    }
}
