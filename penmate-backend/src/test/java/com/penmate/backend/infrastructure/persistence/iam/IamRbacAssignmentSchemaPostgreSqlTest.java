package com.penmate.backend.infrastructure.persistence.iam;

import com.penmate.backend.testinfra.PostgreSqlTestDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class IamRbacAssignmentSchemaPostgreSqlTest {
    private static DataSource dataSource;

    @BeforeAll
    static void migrateSchema() {
        dataSource = PostgreSqlTestDatabase.migratedDataSource("iam_rbac_assignment_schema");
    }

    @Test
    void creates_revision_columns_and_assignment_audit_table() {
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "iam_users")).contains("rbac_revision");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "iam_roles")).contains("rbac_revision");
        assertThat(PostgreSqlTestDatabase.columnsOf(dataSource, "iam_rbac_assignment_audits"))
                .contains("audit_id", "actor_user_id", "assignment_type", "target_id",
                        "before_ids_json", "after_ids_json", "previous_revision", "new_revision", "trace_id");
        assertThat(PostgreSqlTestDatabase.indexesOf(dataSource, "iam_rbac_assignment_audits"))
                .contains("idx_iam_rbac_assignment_audits_target", "idx_iam_rbac_assignment_audits_actor");
    }
}
