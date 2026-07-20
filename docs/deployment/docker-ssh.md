# Docker + SSH 部署指南

PenMate 使用 GitHub Actions 构建前后端镜像并推送到 GHCR，再通过 SSH 更新服务器上的 Docker Compose 栈。关系数据库统一为 PostgreSQL 18.4。

## 服务

- `frontend`：Nginx 托管前端并反向代理 `/api`。
- `backend`：Spring Boot API，仅在 Compose 内部网络暴露 `8080`。
- `postgres`：`postgres:18.4-alpine`，数据卷为 `postgres-data`。
- `redis`：认证、会话和短期缓存。
- `postgres`：PostgreSQL 18.4 + pgvector 0.8.5，同时承载业务数据和向量检索。
- S3 兼容存储：章节、快照、RAG 原始文件和归档。

`docker-compose.yml` 用于本地完整环境并从源码构建镜像；`docker-compose.prod.yml` 从 GHCR 拉取镜像，不向宿主机暴露数据库端口。

## 首次配置

```bash
sudo mkdir -p /opt/penmate
sudo chown "$USER":"$USER" /opt/penmate
cd /opt/penmate
cp .env.example .env
chmod 600 .env
```

至少填写以下值：

```env
DB_NAME=penmate
DB_USER=penmate
DB_PASS=<strong-database-password>
JWT_SECRET=<openssl-rand-base64-32>
MODEL_KEY_ENCRYPTION_KEY_BASE64=<base64-encoded-16-24-or-32-byte-key>
BOOTSTRAP_ADMIN_EMAIL=admin@example.com
BOOTSTRAP_ADMIN_PASSWORD=<strong-admin-password>
BOOTSTRAP_CHAT_PROVIDER=openai
BOOTSTRAP_CHAT_BASE_URL=https://api.openai.com/v1
BOOTSTRAP_CHAT_API_KEY=<model-api-key>
BOOTSTRAP_CHAT_MODEL_NAME=gpt-4o-mini
BOOTSTRAP_EMBEDDING_PROVIDER=openai
BOOTSTRAP_EMBEDDING_BASE_URL=https://api.openai.com/v1
BOOTSTRAP_EMBEDDING_API_KEY=<embedding-api-key>
BOOTSTRAP_EMBEDDING_MODEL_NAME=text-embedding-3-small
```

Bootstrap 默认只创建缺失的一个管理员账户、管理员角色绑定、一个官方模型密钥和一个模型配置。只有临时设置 `BOOTSTRAP_RECONCILE=true` 才会覆盖已存在的 Bootstrap 数据。

## 本地 Compose

```bash
cp .env.example .env
docker compose --env-file .env up -d --build
docker compose --env-file .env ps
curl -fsS http://127.0.0.1:8090/actuator/health
```

停止服务但保留数据：

```bash
docker compose --env-file .env down
```

`down -v` 会删除 PostgreSQL 和 Redis 数据卷，只能在明确不保留数据的本地环境手工执行。CI/CD 不执行该操作。

## CI/CD

后端 CI 使用 PostgreSQL 18.4 Testcontainers 运行测试，不连接部署服务器数据库。部署流程只执行以下操作：

1. 运行后端和前端质量检查。
2. 构建并推送带 commit SHA 和 `latest` 标签的镜像。
3. 上传 Compose 文件和部署脚本。
4. 在服务器拉取镜像并执行 `docker compose up -d`。
5. 检查前端和 `/actuator/health`。

CI/CD 不删除数据库、不清空 schema、不删除 volume，也不迁移旧 MariaDB 数据。旧 MariaDB 容器和数据卷由运维人员手工处理。

## 常用命令

```bash
cd /opt/penmate
docker compose --env-file .env -f docker-compose.prod.yml -p penmate ps
docker compose --env-file .env -f docker-compose.prod.yml -p penmate logs -f backend
docker compose --env-file .env -f docker-compose.prod.yml -p penmate logs -f postgres
docker compose --env-file .env -f docker-compose.prod.yml -p penmate up -d --no-deps backend
```

## PostgreSQL 备份

```bash
cd /opt/penmate
docker compose --env-file .env -f docker-compose.prod.yml -p penmate exec -T postgres \
  pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc > "penmate-$(date +%F).dump"
```

恢复到已创建的空数据库：

```bash
docker compose --env-file .env -f docker-compose.prod.yml -p penmate exec -T postgres \
  pg_restore -U "$DB_USER" -d "$DB_NAME" --clean --if-exists < penmate-YYYY-MM-DD.dump
```

恢复命令会覆盖目标数据库对象，只能由运维人员在确认目标和备份后手工执行。

## Demo 数据

Demo 书籍、Story Bible、Agent、RAG 和插件 case 不属于 Flyway。需要时手工执行：

```bash
./scripts/db/seed-demo.sh
./scripts/db/cleanup-demo.sh
```

这些脚本只处理 `920000` 到 `922999` 的 case 数据，不会由 CI/CD 自动调用。脚本默认拒绝非本机数据库；确认远程目标后，PowerShell 使用 `-AllowRemote`，Bash 使用 `PENMATE_ALLOW_REMOTE_DB=true` 显式放行。
