package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSessionSchemaPostgreSqlContractTest {

    private static DataSource dataSource;

    @BeforeAll
    static void migrateSchema() {
        dataSource = PostgreSqlTestDatabase.migratedDataSource("agent_session_schema_contract");
    }

    @Test
    void should_define_agent_session_run_recovery_tables() {
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_sessions"))
                .contains(
                        "session_id", "bound_style_id", "story_bible_routing_mode",
                        "router_model_config_id", "active_context_epoch_id", "last_run_id",
                        "resumed_at", "total_prompt_tokens", "total_completion_tokens", "total_tokens");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_turns"))
                .contains("turn_id", "turn_seq", "run_id", "resume_token", "turn_status");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_runs"))
                .contains(
                        "run_id", "project_id", "session_id", "turn_id", "run_status", "run_phase",
                        "active_approval_id", "lease_owner", "lease_until", "execution_token",
                        "attempt_count", "next_retry_at", "latest_event_seq", "latest_checkpoint_id");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_run_inputs"))
                .contains(
                        "run_id", "prompt_snapshot", "task_type", "style_snapshot_json",
                        "model_snapshot_json", "plugin_bindings_json", "input_hash");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_events"))
                .contains("run_id", "session_id", "turn_id", "sequence", "schema_version", "event_type", "payload_json");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_checkpoints"))
                .contains("run_id", "checkpoint_no", "last_event_seq", "state_json");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_run_projections"))
                .contains("run_id", "session_id", "turn_id", "run_status", "run_phase", "latest_sequence");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_run_pending_approvals"))
                .contains("run_id", "session_id", "turn_id", "resume_payload_json", "pending_status");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_artifacts"))
                .contains("event_id", "payload_json", "content_type");
    }

    @Test
    void should_not_reintroduce_legacy_agent_tables() {
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_conversations")).isEmpty();
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "pending_tool_invocations")).isEmpty();
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_tasks")).isEmpty();
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_task_contexts")).isEmpty();
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_task_results")).isEmpty();
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "agent_pending_approvals")).isEmpty();
    }

    @Test
    void should_define_agent_uniqueness_and_lookup_indexes() {
        assertThat(PostgreSqlTestDatabase.indexesOf(dataSource, "agent_turns"))
                .contains("uk_agent_turns_turn_id", "uk_agent_turns_session_seq");
        assertThat(PostgreSqlTestDatabase.indexesOf(dataSource, "agent_messages"))
                .contains("uk_agent_messages_message_id", "uk_agent_messages_session_seq");
        assertThat(PostgreSqlTestDatabase.indexesOf(dataSource, "agent_run_pending_approvals"))
                .contains(
                        "uk_agent_run_pending_approvals_approval_id",
                        "uk_agent_run_pending_approvals_idempotency",
                        "idx_agent_run_pending_approvals_run_status",
                        "idx_agent_run_pending_approvals_session_status");
    }
}
