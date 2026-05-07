package com.penmate.backend.infrastructure.persistence.model;

import org.junit.jupiter.api.Test;

/**
 * 已收敛：模型配置最终基线结构已前移到 [`V10__init_plugin_and_model_domains.sql`](penmate-backend/src/main/resources/db/migration/V10__init_plugin_and_model_domains.sql)。
 * 该迁移契约测试此前依赖 H2 执行全量 MySQL 方言 Flyway，会在 [`V1__init_iam_and_rbac.sql`](penmate-backend/src/main/resources/db/migration/V1__init_iam_and_rbac.sql)
 * 处因方言不兼容而失效，因此不再作为当前自动化验证入口保留。
 */
class ModelConfigKeyReferenceMigrationTest {

    @Test
    void deprecated_h2_based_migration_contract_removed_after_baseline_convergence() {
        // no-op
    }
}
