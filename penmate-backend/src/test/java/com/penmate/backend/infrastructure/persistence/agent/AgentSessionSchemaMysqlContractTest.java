package com.penmate.backend.infrastructure.persistence.agent;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSessionSchemaMysqlContractTest {

    private static final String JDBC_URL = "jdbc:h2:mem:agent_session_schema_contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String MIGRATION_DIR = "target/test-migrations/agent-session-schema";

    @BeforeAll
    static void migrateSchema() throws IOException {
        prepareTask2MigrationsOnly();
        Flyway.configure()
                .dataSource(JDBC_URL, "sa", "")
                .locations("filesystem:" + MIGRATION_DIR)
                .load()
                .migrate();
    }

    @Test
    void should_define_agent_session_recovery_tables() throws Exception {
        assertThat(columnsOf("agent_sessions"))
                .contains("session_id", "bound_style_id", "active_context_version", "resumed_at");
        assertThat(columnsOf("agent_turns"))
                .contains("turn_id", "turn_seq", "resume_token", "turn_status");
        assertThat(columnsOf("agent_tasks"))
                .contains("prompt_snapshot", "request_context_id", "result_id", "active_approval_id", "stream_channel_key");
        assertThat(columnsOf("agent_task_contexts"))
                .contains("style_snapshot_json", "model_snapshot_json", "context_hash");
        assertThat(columnsOf("agent_task_results"))
                .contains("output_structured_json", "tool_trace_json");
        assertThat(columnsOf("agent_pending_approvals"))
                .contains("resume_payload_json", "pending_status");

        assertThat(columnsOf("agent_conversations")).isEmpty();
        assertThat(columnsOf("pending_tool_invocations")).isEmpty();

        String v11Sql = Files.readString(Path.of("src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql"));
        String v12Sql = Files.readString(Path.of("src/main/resources/db/migration/V12__init_pending_tool_invocations.sql"));
        assertThat(v11Sql)
                .contains("UNIQUE KEY uk_agent_turns_session_seq (session_id, turn_seq)")
                .contains("UNIQUE KEY uk_agent_messages_session_seq (session_id, seq_no)")
                .contains("轮次状态：PENDING/RUNNING/WAITING_APPROVAL/COMPLETED/FAILED/CANCELLED")
                .contains("任务状态：QUEUED/RUNNING/WAITING_APPROVAL/SUCCEEDED/FAILED/CANCELLED/APPLIED")
                .contains("恢复令牌；显式 resume 时用于校验当前 turn 是否仍对应同一断点")
                .contains("提交执行前冻结的提示词快照；异步恢复与 preflight 重试必须依赖该字段")
                .contains("当前挂起审批单业务 ID；WAITING_APPROVAL 恢复时作为唯一断点指针");
        assertThat(v12Sql)
                .contains("UNIQUE KEY uk_agent_pending_approvals_approval_id (approval_id)")
                .contains("UNIQUE KEY uk_agent_pending_approvals_idempotency_key (idempotency_key)")
                .contains("KEY idx_agent_pending_approvals_session_status (session_id, pending_status)")
                .contains("挂起状态：PENDING/APPROVED/REJECTED/RESUMED/EXPIRED")
                .contains("恢复幂等键；审批恢复重放时用于去重与防止重复执行");
    }

    private Set<String> columnsOf(String tableName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, null)) {
            Set<String> names = new LinkedHashSet<>();
            while (columns.next()) {
                names.add(columns.getString("COLUMN_NAME"));
            }
            return names;
        }
    }

    private static void prepareTask2MigrationsOnly() throws IOException {
        Path migrationDir = Path.of(MIGRATION_DIR);
        Files.createDirectories(migrationDir);
        Files.copy(
                Path.of("src/main/resources/db/migration/V11__init_agent_and_ops_domains.sql"),
                migrationDir.resolve("V11__init_agent_and_ops_domains.sql"),
                StandardCopyOption.REPLACE_EXISTING
        );
        Files.copy(
                Path.of("src/main/resources/db/migration/V12__init_pending_tool_invocations.sql"),
                migrationDir.resolve("V12__init_pending_tool_invocations.sql"),
                StandardCopyOption.REPLACE_EXISTING
        );
    }

}
