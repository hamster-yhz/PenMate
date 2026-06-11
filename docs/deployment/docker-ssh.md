# Docker + SSH 部署指南

本文档描述如何将 PenMate 通过 Docker Compose 部署到一�?Linux 服务器。GitHub Actions �?CI 流水线上构建前后端镜像并推送到 GHCR，然后通过 SSH 连接到服务器更新运行中的 Compose 栈。所有容器（frontend、backend、MySQL、Redis、Milvus、etcd）运行在同一台部署服务器上�?
## 两套 Compose 文件的区�?
| 文件 | 用�?| 关键差异 |
|---|---|---|
| `docker-compose.yml` | 本地开�?| �?`build:` 从源码构建镜像；暴露数据库端口方便调�?|
| `docker-compose.prod.yml` | 生产部署 | �?`image:` �?GHCR 拉取 CI 预构建的镜像；不暴露数据库端�?|

本地开发时�?`docker-compose.yml`（`docker compose up --build`），生产服务器上�?`docker-compose.prod.yml`（`docker compose pull && docker compose up -d`）�?
## 运行时架�?
- `frontend`：Nginx 提供 Vite 构建产物，监�?`FRONTEND_PUBLIC_PORT`（默�?80）。将域名流量指向此端口；TLS 可在 Cloudflare 侧终结�?- `backend`：Spring Boot API，运行在内部 Docker 网络，不对外暴露端口�?- `mysql`：MySQL 8.4，数据持久化�?`mysql-data` 卷�?- `redis`：Redis 7，开�?AOF 持久化�?- 外部 S3 兼容存储：供后端存储章节/RAG 资产，以�?Milvus 存储向量段数据�?- `milvus`：Milvus standalone 向量数据库，依赖 `etcd` 和外�?S3 桶�?- `etcd`：Milvus 的元数据存储�?- `rabbitmq`：可选，通过 profile 启动。当前后�?Maven AMQP 依赖已注释掉，默认不启动�?
前端�?`/api` �?API 基础路径，Nginx �?`/api/` 反向代理�?`backend:8080/api/`。SSE 端点通过同一代理处理，已关闭缓冲。`VITE_STORAGE_URL_PROTOCOL` 设置�?`http` 是因为浏览器通过 Nginx 访问，Nginx 再转发到后端，内网通信�?HTTP�?
`STORAGE_ENDPOINT` 设置�?S3 兼容�?API 地址，`STORAGE_PUBLIC_ENDPOINT` 设置为浏览器可直接访问的地址（用于生成预签名上传/下载 URL）。Milvus �?`MILVUS_S3_*` 变量指向同一或独立的 S3 �?前缀�?
## 本地 WSL Docker 开�?
前提：WSL 内已安装 Docker，且 Windows 宿主机上�?Maven�?
首先在宿主机上打包后�?jar�?
```powershell
cd D:\warehouse\project\PenMate\penmate-backend
mvn -B -DskipTests package
```

然后�?WSL 中启�?Compose�?
```bash
cd /mnt/d/warehouse/project/PenMate
cp .env.example .env
# 编辑 .env，填入本地可用值。建议保�?LLM_MOCK_ENABLED=true 用于冒烟测试�?docker compose --env-file .env up -d --build
docker compose --env-file .env ps
curl -fsS http://127.0.0.1:8090/
curl -fsS http://127.0.0.1:8090/actuator/health
```

> **注意�?* 后端 Docker 镜像�?`penmate-backend/target` 复制 jar 文件，因此必须先打包后端再执�?`docker compose --build`。如�?WSL �?npm 下载慢，可在 `.env` 中设置镜像源�?>
> ```bash
> NPM_CONFIG_REGISTRY=https://registry.npmmirror.com
> ```

停止本地服务�?
```bash
docker compose --env-file .env down
```

仅在需要清空数据库时删除数据卷�?
```bash
docker compose --env-file .env down -v
```

## 服务器初次配�?
�?Linux 服务器上安装 Docker Engine �?Compose 插件。然后创建部署目录：

```bash
sudo mkdir -p /opt/penmate
sudo chown "$USER":"$USER" /opt/penmate
```

�?`.env.example` 复制�?`/opt/penmate/.env` 并填入生产环境配置：

```bash
cd /opt/penmate
cp .env.example .env
chmod 600 .env
```

用以下命令生成安全的随机值：

```bash
openssl rand -base64 32
```

将生成的随机值填入以下变量：`JWT_SECRET`、`DB_PASS`、`DB_ROOT_PASS`、`REDIS_PASS`、`STORAGE_ACCESS_KEY`、`STORAGE_SECRET_KEY`、`MODEL_KEY_ENCRYPTION_KEY_BASE64`。同时填�?`STORAGE_ENDPOINT`、`STORAGE_PUBLIC_ENDPOINT` 以及 `MILVUS_S3_*` 变量，指向你�?S3 服务。`MODEL_KEY_ENCRYPTION_KEY_BASE64` 解码后必须为 16�?4 �?32 字节�?
## GitHub Secrets

需要在仓库或环境级别配置以�?Secrets�?
**必填�?*

- `SSH_HOST`：服务器主机名或 IP�?- `SSH_USER`：可执行 Docker 命令�?Linux 用户�?- `SSH_PRIVATE_KEY`：SSH 部署私钥�?- `GHCR_USERNAME`：GHCR 用户名（�?push 镜像时登�?GHCR 使用同一账号）�?- `GHCR_TOKEN`：GHCR 访问令牌，需具备 `read:packages` �?`write:packages` 权限（与 push 镜像时登�?GHCR 使用同一令牌）�?
**选填�?*

- `DEPLOY_PATH`：部署路径，默认 `/opt/penmate`�?
CI 流水线不上传生产 `.env` 文件，请仅在服务器上保管�?
## 部署流程

当代码推送到 `main` �?`master` 分支，或手动触发 `workflow_dispatch` 时，`.github/workflows/deploy.yml` 执行以下步骤�?
1. 后端 `mvn -B verify`（含测试和覆盖率门禁）�?2. 前端 `npm ci` �?`npm run test:coverage`�?3. Docker 构建后端和前端镜像，打上 commit SHA �?`latest` 两个 tag，推送到 GHCR。登�?GHCR 使用 `GHCR_USERNAME` + `GHCR_TOKEN`�?4. �?`docker-compose.prod.yml`、`.env.example` 及脚本打包上传到服务器的 `DEPLOY_PATH`�?5. SSH 执行 `scripts/deploy-remote.sh` 完成部署�?6. 健康检查：通过前端�?Nginx 代理验证 `/actuator/health` �?`/` 均可访问�?
手动部署通过 GitHub Actions �?`workflow_dispatch` 触发�?
## 回滚流程

通过 GitHub Actions 手动触发 `.github/workflows/rollback.yml`，回滚到指定的镜像版本。需提供�?
- `backend_image`：完整的后端镜像 tag，通常是上一�?commit �?SHA tag�?- `frontend_image`：完整的前端镜像 tag，通常是上一�?commit �?SHA tag�?
回滚 CI 会上传当前部署包，登�?GHCR（使�?`GHCR_USERNAME` + `GHCR_TOKEN`），拉取指定镜像，重�?Compose，并执行相同的健康检查。回滚不会修改服务器上的 `.env` 文件，仅当次运行使用指定的镜�?tag�?
## 服务器常用命�?
查看服务状态：

```bash
cd /opt/penmate
docker compose --env-file .env -f docker-compose.prod.yml -p penmate ps
```

查看日志�?
```bash
docker compose --env-file .env -f docker-compose.prod.yml -p penmate logs -f backend
docker compose --env-file .env -f docker-compose.prod.yml -p penmate logs -f frontend
```

单独重启某个服务�?
```bash
docker compose --env-file .env -f docker-compose.prod.yml -p penmate up -d --no-deps backend
```

启动可选的 RabbitMQ�?
```bash
docker compose --profile rabbitmq --env-file .env -f docker-compose.prod.yml -p penmate up -d rabbitmq
```

## 手动回滚（不经过 CI�?
GHCR 镜像�?commit SHA �?tag。如需手动回滚�?
1. 编辑 `/opt/penmate/.env`�?2. �?`BACKEND_IMAGE` �?`FRONTEND_IMAGE` 设置为上一次的 SHA tag�?3. 执行�?
```bash
cd /opt/penmate
./scripts/deploy-remote.sh
```

## 备份

至少备份以下内容�?
- `mysql-data` 卷：应用数据库�?- `redis-data` 卷：认证/会话/TODO 缓存数据�?- `milvus-data` �?`etcd-data` 卷：向量数据库状态�?- `/opt/penmate/.env`：生产配置和密钥�?- 后端�?Milvus 使用的外�?S3 桶�?
MySQL 推荐使用计划的逻辑备份�?
```bash
docker compose --env-file .env -f docker-compose.prod.yml -p penmate exec mysql \
  mysqldump -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" > "penmate-$(date +%F).sql"
```

建议将此命令加入 crontab 实现每日自动备份�?
