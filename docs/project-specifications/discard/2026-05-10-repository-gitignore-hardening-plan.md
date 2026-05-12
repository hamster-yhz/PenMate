# Repository .gitignore Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use [executing-plans] mode to implement this plan task-by-task.

**Goal:** 为 [`/.gitignore`](.gitignore) 产出一份面向 PenMate 仓库的、安全且可直接执行的更新方案，确保推送到 GitHub 前排除构建产物、日志、环境变量、IDE 垃圾、覆盖率与本地缓存，同时不误排除锁文件、源码资源和文档。

**Architecture:** 该仓库是前后端分离的 monorepo：后端为 Maven + Spring Boot + Java 21，前端为 Vite + Vue 3 + TypeScript + Vitest。实施方式是在仓库根目录集中维护一份跨子项目的 [`/.gitignore`](.gitignore)，吸收 [`penmate-frontend/.gitignore`](penmate-frontend/.gitignore) 中对前端产物的约束，同时补充后端运行时日志、JVM 崩溃文件、覆盖率目录、本地环境文件与未来高概率生成的缓存目录。

**Tech Stack:** Git, GitHub, Java 21, Spring Boot 3.3, Maven, JaCoCo, Vue 3, TypeScript, Vite, Vitest, npm/pnpm, Windows 11, IntelliJ IDEA, VS Code

---

## Repository Findings

### Confirmed current structure
- Root: [`/.gitignore`](.gitignore), [`/.github/`](.github/), [`/docs/`](docs/), [`/penmate-backend/`](penmate-backend/), [`/penmate-frontend/`](penmate-frontend/)
- Backend manifest: [`penmate-backend/pom.xml`](penmate-backend/pom.xml)
- Frontend manifest: [`penmate-frontend/package.json`](penmate-frontend/package.json)
- Frontend nested ignore file: [`penmate-frontend/.gitignore`](penmate-frontend/.gitignore)

### Confirmed current generated/local files already present in workspace
- Backend local env file: [`penmate-backend/.env`](penmate-backend/.env)
- Backend JVM crash logs: [`penmate-backend/hs_err_pid3616.log`](penmate-backend/hs_err_pid3616.log) and similar `hs_err_pid*.log`
- Backend JVM replay logs: [`penmate-backend/replay_pid3616.log`](penmate-backend/replay_pid3616.log) and similar `replay_pid*.log`
- Backend local scratch file: [`penmate-backend/tmp_model_pref_check.txt`](penmate-backend/tmp_model_pref_check.txt)
- Frontend local env file: [`penmate-frontend/.env.development`](penmate-frontend/.env.development)
- Frontend logs: [`penmate-frontend/frontend.log`](penmate-frontend/frontend.log), [`penmate-frontend/frontend.err.log`](penmate-frontend/frontend.err.log)
- Frontend coverage report directory: [`penmate-frontend/coverage/`](penmate-frontend/coverage/)
- Root IDE file: [`/PenMate.iml`](PenMate.iml)

### Confirmed technology-specific implications
- Maven will generate `target/`, Surefire/Failsafe reports, packaged JAR/WARs, JaCoCo exec/html/xml outputs.
- Vite/Vitest will generate `node_modules/`, `dist/`, optional `dist-ssr/`, coverage reports, TypeScript incremental metadata in `node_modules/.tmp`, and possibly cache directories.
- The repository currently contains both [`penmate-frontend/package-lock.json`](penmate-frontend/package-lock.json) and [`penmate-frontend/pnpm-lock.yaml`](penmate-frontend/pnpm-lock.yaml); this plan explicitly preserves lockfiles and does **not** add ignore rules for any lockfile.

## Target Outcome

After execution, root [`/.gitignore`](.gitignore) should:
1. Cover current frontend and backend generated files.
2. Anticipate future generated files for Maven, Vite, Vitest, Node package managers, local DBs, and IDEs.
3. Preserve tracked source assets such as [`penmate-frontend/src/assets/images/logo.png`](penmate-frontend/src/assets/images/logo.png), markdown docs like [`docs/plans/2026-05-10-agent-prompt-routing-and-preflight-plan.md`](docs/plans/2026-05-10-agent-prompt-routing-and-preflight-plan.md), and lockfiles such as [`penmate-frontend/package-lock.json`](penmate-frontend/package-lock.json).
4. Be validated with `git status --short --ignored` and representative `git check-ignore -v` probes.

---

### Task 1: Baseline repository inventory and ignore-risk matrix

**Files:**
- Modify: [`docs/plans/2026-05-10-repository-gitignore-hardening-plan.md`](docs/plans/2026-05-10-repository-gitignore-hardening-plan.md)
- Review: [`/.gitignore`](.gitignore)
- Review: [`penmate-frontend/.gitignore`](penmate-frontend/.gitignore)
- Review: [`penmate-backend/pom.xml`](penmate-backend/pom.xml)
- Review: [`penmate-frontend/package.json`](penmate-frontend/package.json)

**Step 1: Write the failing verification checklist**
Create this checklist in a temporary working note or task description before touching [`/.gitignore`](.gitignore):

```md
- [ ] `penmate-backend/.env` is ignored
- [ ] `penmate-frontend/.env.development` is ignored
- [ ] `penmate-frontend/coverage/index.html` is ignored
- [ ] `penmate-frontend/frontend.log` is ignored
- [ ] `penmate-backend/hs_err_pid3616.log` is ignored
- [ ] `PenMate.iml` is ignored
- [ ] `penmate-frontend/package-lock.json` is NOT ignored
- [ ] `penmate-frontend/pnpm-lock.yaml` is NOT ignored
- [ ] `penmate-frontend/src/assets/images/logo.png` is NOT ignored
- [ ] `docs/plans/2026-05-10-agent-prompt-routing-and-preflight-plan.md` is NOT ignored
```

**Step 2: Run verification to show the current gaps**
Run:

```bat
git status --short --ignored
```

Expected:
- You should see at least some currently untracked/generated files in [`penmate-backend/`](penmate-backend/) and [`penmate-frontend/`](penmate-frontend/).
- You may also see files that are already ignored by nested [`penmate-frontend/.gitignore`](penmate-frontend/.gitignore), proving the root file does not yet fully centralize policy.

Run targeted probes:

```bat
git check-ignore -v penmate-backend/.env penmate-frontend/.env.development penmate-frontend/coverage/index.html penmate-frontend/package-lock.json penmate-frontend/src/assets/images/logo.png PenMate.iml
```

Expected:
- Some paths will already report an ignore rule.
- At least one repository-local artifact category should show policy fragmentation or missing root-level coverage, justifying the root [`/.gitignore`](.gitignore) hardening.

**Step 3: Record the inventory conclusions**
Document these conclusions in the working notes:
- Backend ignores must include runtime env, logs, JVM crash dumps, Maven outputs, JaCoCo outputs.
- Frontend ignores must include local env variants, logs, coverage, build outputs, Node caches.
- Root ignores must include IDE and OS files.
- Do **not** ignore lockfiles, source assets, public assets, markdown docs, Java resources under `src/main/resources`, or frontend files under `src/`/`public/`.

**Step 4: Re-run quick spot checks after notes are captured**
Run:

```bat
git check-ignore -v penmate-backend/.env penmate-frontend/frontend.log penmate-backend/hs_err_pid3616.log
```

Expected:
- This confirms the baseline before editing and gives comparison points for final verification.

**Step 5: Commit the inventory checkpoint**
If you are tracking planning work separately, use:

```bat
git add docs/plans/2026-05-10-repository-gitignore-hardening-plan.md && git commit -m "docs: add gitignore hardening plan"
```

Expected:
- A docs-only commit containing the plan.

---

### Task 2: Replace root [`/.gitignore`](.gitignore) with a unified repository policy

Use [test-driven-development] mode for this task.

**Files:**
- Modify: [`/.gitignore:1-49`](.gitignore)
- Review only: [`penmate-frontend/.gitignore:1-25`](penmate-frontend/.gitignore:1)

**Step 1: Write the failing verification expectation**
Before editing [`/.gitignore`](.gitignore), define the expected final behaviors:

```md
Expected ignored:
- /penmate-backend/.env
- /penmate-backend/*.log
- /penmate-backend/hs_err_pid*.log
- /penmate-backend/replay_pid*.log
- /penmate-backend/tmp_*.txt
- /penmate-frontend/.env.*
- /penmate-frontend/node_modules/
- /penmate-frontend/dist/
- /penmate-frontend/dist-ssr/
- /penmate-frontend/coverage/
- /penmate-frontend/*.log
- **/target/
- **/build/
- **/.idea/
- **/.vscode/

Expected NOT ignored:
- /penmate-frontend/package-lock.json
- /penmate-frontend/pnpm-lock.yaml
- /penmate-frontend/src/assets/**
- /penmate-frontend/public/**
- /penmate-backend/src/main/resources/**
- /docs/**
```

**Step 2: Replace root [`/.gitignore`](.gitignore) with this exact content**

```gitignore
# PenMate monorepo ignore policy

### Build outputs ###
**/target/
!**/src/main/**/target/
!**/src/test/**/target/
**/build/
!**/src/main/**/build/
!**/src/test/**/build/
**/dist/
**/dist-ssr/
**/coverage/

### Node / frontend dependencies and caches ###
**/node_modules/
**/.pnpm-store/
**/.npm/
**/.yarn/
**/.turbo/
**/.cache/
**/.vite/
**/*.tsbuildinfo

### Java / Maven / JVM ###
*.class
*.jar
*.war
*.ear
*.nar
*.hprof
hs_err_pid*
replay_pid*
**/.mvn/wrapper/maven-wrapper.jar
!/.mvn/wrapper/maven-wrapper.jar

### Test reports ###
**/surefire-reports/
**/failsafe-reports/
**/test-results/
**/jacoco.exec
**/jacoco*.exec
**/site/jacoco/

### Logs ###
logs/
*.log
npm-debug.log*
yarn-debug.log*
yarn-error.log*
pnpm-debug.log*
lerna-debug.log*

### Environment and local config ###
.env
.env.*
!.envexample
!.env.sample
!.env.template
!.env.example
*.local

### Local databases / storage ###
*.db
*.sqlite
*.sqlite3
*.mv.db
*.trace.db
redis-data/

### IDE / editor ###
.idea/
.vscode/
!.vscode/extensions.json
*.iml
*.iws
*.ipr
*.suo
*.ntvs*
*.njsproj
*.sln
*.sw?
.classpath
.factorypath
.project
.settings/
.apt_generated/
.springBeans
.sts4-cache/

### OS files ###
.DS_Store
Thumbs.db
Desktop.ini

### Project-specific local scratch / diagnostics ###
penmate-backend/.env
penmate-backend/tmp_*.txt
penmate-backend/*.log
penmate-backend/hs_err_pid*.log
penmate-backend/replay_pid*.log
penmate-frontend/.env.local
penmate-frontend/.env.development.local
penmate-frontend/.env.test.local
penmate-frontend/.env.production.local
penmate-frontend/*.log
penmate-frontend/coverage/

### Workspace tooling ###
.roo/
.roomodes
```

Implementation notes:
- Keep the general `*.log` rule because it matches current [`penmate-frontend/frontend.log`](penmate-frontend/frontend.log) and backend `.log` files.
- Keep all `!**/src/.../target/` and `!**/src/.../build/` exceptions to avoid accidentally hiding legitimately named source directories.
- Do **not** add patterns for `package-lock.json`, `pnpm-lock.yaml`, `pom.xml`, `README.md`, `docs/`, `public/`, `src/assets/`, or backend prompt markdown resources.
- The line `**/.mvn/wrapper/maven-wrapper.jar` is intentionally negated by `!/.mvn/wrapper/maven-wrapper.jar`; if no root wrapper exists today, this remains future-safe and should not harm tracked wrapper adoption.

**Step 3: Run targeted verification after editing**
Run:

```bat
git check-ignore -v penmate-backend/.env penmate-frontend/.env.development penmate-frontend/coverage/index.html penmate-frontend/frontend.log penmate-backend/hs_err_pid3616.log PenMate.iml penmate-frontend/package-lock.json penmate-frontend/pnpm-lock.yaml penmate-frontend/src/assets/images/logo.png docs/plans/2026-05-10-agent-prompt-routing-and-preflight-plan.md
```

Expected:
- Ignored paths show the matching rule and source file [`/.gitignore`](.gitignore).
- Lockfiles, source asset files, and docs produce no ignore match.

**Step 4: Run repository-wide verification**
Run:

```bat
git status --short --ignored
```

Expected:
- Generated/frontend/backend local artifacts should move under the ignored set.
- Source files, docs, lockfiles, and public assets remain visible to Git when untracked or modified.

**Step 5: Commit the root policy update**
Run:

```bat
git add .gitignore && git commit -m "chore: harden repository gitignore"
```

Expected:
- One isolated commit containing only the root ignore policy.

---

### Task 3: Validate that no required tracked artifacts become collateral damage

**Files:**
- Verify only: [`penmate-frontend/package-lock.json`](penmate-frontend/package-lock.json)
- Verify only: [`penmate-frontend/pnpm-lock.yaml`](penmate-frontend/pnpm-lock.yaml)
- Verify only: [`penmate-frontend/src/assets/images/logo.png`](penmate-frontend/src/assets/images/logo.png)
- Verify only: [`penmate-frontend/public/favicon.svg`](penmate-frontend/public/favicon.svg)
- Verify only: [`penmate-backend/src/main/resources/prompts/agent/system/execution/default/20-tool-use-policy.md`](penmate-backend/src/main/resources/prompts/agent/system/execution/default/20-tool-use-policy.md)
- Verify only: [`docs/plans/2026-05-10-agent-prompt-routing-and-preflight-plan.md`](docs/plans/2026-05-10-agent-prompt-routing-and-preflight-plan.md)

**Step 1: Write the failing safety assertions**
Document these required non-ignore assertions:

```md
- Lockfiles remain versioned because they define deterministic dependency graphs.
- Frontend images and icons remain versioned because they are shipped app assets.
- Backend markdown prompt resources remain versioned because they are runtime application resources.
- Plan and project docs remain versioned because they are source-of-truth documentation.
```

**Step 2: Run negative ignore checks**
Run:

```bat
git check-ignore -v penmate-frontend/package-lock.json penmate-frontend/pnpm-lock.yaml penmate-frontend/src/assets/images/logo.png penmate-frontend/public/favicon.svg penmate-backend/src/main/resources/prompts/agent/system/execution/default/20-tool-use-policy.md docs/plans/2026-05-10-agent-prompt-routing-and-preflight-plan.md
```

Expected:
- No output for these files.
- If output appears, the new rule set is too broad and must be corrected before commit.

**Step 3: If any false positive appears, make the minimal correction**
Allowed corrective examples:

```gitignore
# Example unignore exceptions if a future broad rule causes damage
!penmate-frontend/package-lock.json
!penmate-frontend/pnpm-lock.yaml
!penmate-frontend/src/assets/**
!penmate-frontend/public/**
!penmate-backend/src/main/resources/**
!docs/**
```

Only add these if actual verification proves a false positive. Do not pre-emptively overcomplicate the file.

**Step 4: Re-run the negative checks**
Run the same `git check-ignore -v` command again.

Expected:
- Zero matches for the required tracked artifacts.

**Step 5: Commit the safety fix if needed**
Run only if Step 3 changed [`/.gitignore`](.gitignore):

```bat
git add .gitignore && git commit -m "fix: preserve tracked assets in gitignore rules"
```

Expected:
- A narrowly scoped follow-up fix commit.

---

### Task 4: Optional cleanup of already-generated local garbage before pushing

**Files:**
- Delete locally if untracked and unnecessary: [`penmate-backend/hs_err_pid3616.log`](penmate-backend/hs_err_pid3616.log) and other `hs_err_pid*.log`
- Delete locally if untracked and unnecessary: [`penmate-backend/replay_pid3616.log`](penmate-backend/replay_pid3616.log) and other `replay_pid*.log`
- Delete locally if untracked and unnecessary: [`penmate-backend/tmp_model_pref_check.txt`](penmate-backend/tmp_model_pref_check.txt)
- Delete locally if untracked and unnecessary: [`penmate-frontend/frontend.log`](penmate-frontend/frontend.log)
- Delete locally if untracked and unnecessary: [`penmate-frontend/frontend.err.log`](penmate-frontend/frontend.err.log)
- Delete locally if untracked and unnecessary: [`penmate-frontend/coverage/`](penmate-frontend/coverage/)

**Step 1: Write the failing cleanliness expectation**
Define the final cleanliness rule:

```md
After cleanup, `git status --short --ignored` should not show noisy untracked crash dumps, replay logs, coverage HTML, or ad-hoc scratch files as pending for staging decisions.
```

**Step 2: Remove obsolete ignored artifacts from the working tree**
Run only if you want a cleaner local workspace before push:

```bat
del /q penmate-backend\hs_err_pid*.log && del /q penmate-backend\replay_pid*.log && del /q penmate-backend\tmp_*.txt && del /q penmate-frontend\*.log && rmdir /s /q penmate-frontend\coverage
```

Expected:
- Files are deleted locally.
- Because they are ignored, they do not return to Git status unless regenerated.

**Step 3: Re-run status to verify cleanliness**
Run:

```bat
git status --short --ignored
```

Expected:
- The repository is materially quieter, with only intentional tracked changes remaining.

**Step 4: Verify no tracked file was removed**
Run:

```bat
git status --short
```

Expected:
- No accidental deletions of tracked source files, docs, assets, or lockfiles.

**Step 5: Commit only if cleanup required tracked changes**
Normally no commit is needed for ignored-file deletion. If any tracked file was touched by mistake, restore it before proceeding.

---

### Task 5: Final pre-push review and handoff

Use [finishing-a-development-branch] mode for this task.

**Files:**
- Verify: [`/.gitignore`](.gitignore)
- Verify: [`docs/plans/2026-05-10-repository-gitignore-hardening-plan.md`](docs/plans/2026-05-10-repository-gitignore-hardening-plan.md)

**Step 1: Write the final acceptance checklist**

```md
- [ ] Root `.gitignore` covers backend + frontend + IDE + OS + logs + coverage + env + cache + local DB cases
- [ ] No lockfile is ignored
- [ ] No source asset, runtime resource, or docs file is ignored
- [ ] `git status --short --ignored` output is consistent with expectations
- [ ] Changes are isolated and commit-ready for GitHub push
```

**Step 2: Run the final verification commands**
Run:

```bat
git check-ignore -v penmate-backend/.env penmate-frontend/.env.development penmate-frontend/coverage/index.html penmate-frontend/frontend.log penmate-backend/hs_err_pid3616.log PenMate.iml
```

Then run:

```bat
git check-ignore -v penmate-frontend/package-lock.json penmate-frontend/pnpm-lock.yaml penmate-frontend/src/assets/images/logo.png docs/plans/2026-05-10-agent-prompt-routing-and-preflight-plan.md
```

Then run:

```bat
git status --short --ignored
```

Expected:
- First command returns matching ignore rules.
- Second command returns no matches.
- Third command shows a clean, understandable ignore boundary.

**Step 3: Prepare the final reviewer summary**
Include these bullets in the PR or push notes:
- Root ignore policy is now monorepo-aware.
- Frontend nested ignore knowledge has been absorbed into root policy where appropriate.
- Current noisy local artifacts found in workspace were explicitly covered.
- Future build/cache/coverage/database artifacts were preemptively covered.
- Lockfiles and real project assets were explicitly protected by verification.

**Step 4: Confirm commit history**
Run:

```bat
git log --oneline -n 3
```

Expected:
- Recent commits clearly separate docs planning, ignore policy change, and any safety follow-up.

**Step 5: Push-ready state**
Run:

```bat
git status
```

Expected:
- Working tree is ready for the next human decision: push or additional review.

---

## Notes on rules that should stay out of [`/.gitignore`](.gitignore)

Do **not** add any of the following ignore patterns unless the repository strategy changes intentionally:

```gitignore
package-lock.json
pnpm-lock.yaml
*.png
*.svg
*.md
src/
public/
docs/
```

Reasoning:
- [`penmate-frontend/package-lock.json`](penmate-frontend/package-lock.json) and [`penmate-frontend/pnpm-lock.yaml`](penmate-frontend/pnpm-lock.yaml) are dependency lockfiles and must not be blanket-ignored.
- Assets under [`penmate-frontend/src/assets/`](penmate-frontend/src/assets/) and [`penmate-frontend/public/`](penmate-frontend/public/) are application source artifacts.
- Markdown under [`docs/`](docs/) and backend prompt resources under [`penmate-backend/src/main/resources/prompts/`](penmate-backend/src/main/resources/prompts/) are versioned source content.

## Direct `.gitignore` update recommendation

If implementation is requested with no further redesign, use the exact replacement block from **Task 2 / Step 2** as the starting point for [`/.gitignore`](.gitignore).
