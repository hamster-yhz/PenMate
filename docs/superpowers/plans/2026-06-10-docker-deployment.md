# Docker Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Docker-based deployment for the PenMate frontend, backend, runtime dependencies, GitHub CI validation, and SSH-based production deployment.

**Architecture:** Build two application images: a Spring Boot backend image and an Nginx-served Vue frontend image. Run them on a single Linux server through Docker Compose with MySQL, Redis, MinIO, and Milvus as local services; CI builds/tests both apps and deploys from GHCR over SSH.

**Tech Stack:** Docker, Docker Compose, GitHub Actions, GHCR, Spring Boot 3 / Java 21, Vue/Vite / Node 22, Nginx, MySQL 8, Redis 7, MinIO, Milvus standalone.

---

### Task 1: Container Build Files

**Files:**
- Create: `penmate-backend/Dockerfile`
- Create: `penmate-frontend/Dockerfile`
- Create: `penmate-frontend/nginx.conf`
- Create: `.dockerignore`

- [ ] **Step 1: Add backend Dockerfile**

Create a multi-stage Maven build using Eclipse Temurin 21 and run the resulting jar as a non-root user.

- [ ] **Step 2: Add frontend Dockerfile and Nginx config**

Build the Vue app with Node 22 and serve `dist` from Nginx. Proxy `/api` and agent stream endpoints to `backend:8080`, with buffering disabled for SSE.

- [ ] **Step 3: Add monorepo Docker ignore**

Exclude worktrees, build outputs, logs, local env files, IDE files, and dependency folders from Docker build contexts.

### Task 2: Compose and Environment Templates

**Files:**
- Create: `docker-compose.yml`
- Create: `docker-compose.prod.yml`
- Create: `.env.deploy.example`

- [ ] **Step 1: Add local Compose stack**

Define `mysql`, `redis`, `minio`, `milvus` dependencies and `backend`/`frontend` app services using local Docker builds.

- [ ] **Step 2: Add production Compose stack**

Define the same runtime shape but pull `${BACKEND_IMAGE}` and `${FRONTEND_IMAGE}` from GHCR instead of building locally.

- [ ] **Step 3: Add deployment environment template**

Document every variable the Compose files use, including database credentials, JWT/encryption keys, storage keys, LLM settings, image names, and public ports.

### Task 3: Deployment Scripts

**Files:**
- Create: `scripts/deploy-remote.sh`
- Create: `scripts/healthcheck.sh`

- [ ] **Step 1: Add remote deployment script**

On the server, validate Docker Compose availability, ensure the deployment directory exists, log in to GHCR when credentials are provided, pull images, start the stack, and run a health check.

- [ ] **Step 2: Add health check script**

Check the backend actuator health endpoint and frontend HTTP endpoint with retry loops suitable for CI and SSH deployment.

### Task 4: GitHub Actions

**Files:**
- Modify: `.github/workflows/tdd-quality-gate.yml`
- Create: `.github/workflows/deploy.yml`

- [ ] **Step 1: Extend quality gate**

Keep backend and frontend tests, then add Docker image build checks for both Dockerfiles.

- [ ] **Step 2: Add deployment workflow**

On pushes to `main`/`master` or manual dispatch, run tests, build/push GHCR images, copy Compose/script files to the server over SSH, and invoke the remote deployment script.

### Task 5: Documentation and Verification

**Files:**
- Create: `docs/deployment/docker-ssh.md`

- [ ] **Step 1: Add operator guide**

Explain local WSL Docker usage, required server packages, GitHub Secrets, first-time server setup, deploy flow, rollback, logs, and backup notes.

- [ ] **Step 2: Run verification**

Run frontend build, backend package/tests as feasible, workflow YAML parse checks, shell script syntax checks if available, and Docker Compose config checks if Docker is available. If Windows cannot reach WSL Docker, document the exact WSL commands to run.

### Self-Review

Spec coverage: The plan covers Docker deployment for frontend/backend/dependencies, CI validation, SSH deployment CI, and local WSL Docker guidance.

Placeholder scan: No placeholder implementation steps are left; secrets are intentionally represented as environment variables.

Type consistency: Service names are `backend`, `frontend`, `mysql`, `redis`, `minio`, and `milvus` throughout the planned artifacts.
