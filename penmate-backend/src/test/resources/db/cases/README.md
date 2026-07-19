# PostgreSQL Demo And Test Cases

These files are explicit demo/test data and are not Flyway migrations. Flyway V7 contains only required non-secret system metadata; administrator and model credentials are created by application bootstrap.

## Seed Order

1. `seed/01_book_and_story_bible.sql`
2. `seed/02_agent.sql`
3. `seed/03_rag_and_storage.sql`
4. `seed/04_plugin.sql`
5. `seed/05_boundary.sql`
6. `seed/06_concurrency.sql`

The first four files form one inspectable book workflow. Boundary and concurrency rows are independent fixtures for focused testing. All case IDs use the `920000` to `922999` range.

Run all files through `scripts/db/seed-demo.ps1` or `scripts/db/seed-demo.sh`. Remove only these rows with the matching cleanup script, which executes `cleanup.sql`.

The scripts expect an already migrated PostgreSQL database. They refuse non-local hosts by default; use PowerShell `-AllowRemote` or Bash `PENMATE_ALLOW_REMOTE_DB=true` only after checking the target. They do not create databases, run Flyway, reset schemas, or modify CI/CD deployment state.
