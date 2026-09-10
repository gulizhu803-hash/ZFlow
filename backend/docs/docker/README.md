# Docker 运行说明

项目已具备完整的 Docker 部署：Vue + Nginx 前端、Spring Boot 后端，以及 MySQL、pgvector、Redis、RabbitMQ。默认启动这 6 个服务；OneAPI 和 MCP 按需启用。

## 当前工作区启动

本次已生成本地 `.env`，数据库密码与 JWT secret 为随机值；该文件已被 Git 忽略。模型 API 与 OSS 保留未配置标记。

在项目根目录打开 PowerShell：

```powershell
Set-Location backend/docs/docker
docker compose up -d --no-build --pull never --wait --wait-timeout 300 frontend
docker compose ps
```

网页入口：<http://localhost:18080/miniagent/>，可直接注册自己的账号。
后端健康接口：<http://localhost:18080/miniagent/api/v1/actuator/health>，正常返回 `{"status":"UP"}`。

前端通过同源 `/miniagent/api/` 调用后端，Nginx 保留请求路径并关闭 SSE 代理缓冲。后端仅在 Docker 网络内开放 8066。当前工作区前后端镜像已构建，上面的命令直接复用镜像；日常启动不需要宿主机安装 Java、Maven 或 Node。

前后端与五个 MCP 服务均设置 `pull_policy: never`，避免向 Docker Hub 拉取仅存在本地的应用镜像。正常 `up` 会复用现有镜像；没有镜像时会尝试源码构建。当前 Docker Hub 不可达，需要重建时使用下方“Docker Hub 不可达时构建前后端”的步骤。

## 在新机器部署

1. 安装并启动 Docker Desktop，使用 Linux 容器。
2. 在本目录将 `.env.example` 复制为 `.env`；已存在时不要覆盖。
3. 将 `PASSWORD` 与 `JWT_SECRET` 改为自己的随机值，检查端口。
4. 在本目录执行 `docker compose up -d --build --wait --wait-timeout 300 frontend`。此源码构建方式要求能够访问 Docker Hub；不可达时按下节先在本机打包并构建应用镜像。

```powershell
# 仅在 .env 不存在时复制
if (!(Test-Path .env)) { Copy-Item .env.example .env }
docker compose config --quiet
```

`MINIAGENT_USER` 默认是 `miniagent`，不能填写 `root`。没有使用 Windows 自带的 `USERNAME` 变量，以免覆盖配置。密码含 `$` 或 `#` 时可使用 dotenv 单引号格式。

跨域默认只允许 `localhost` 和 `127.0.0.1`。前后端分域部署时，在 `.env` 中配置 `ZFLOW_WEB_ALLOWED_ORIGIN_PATTERNS=https://你的前端域名`（多个来源使用逗号分隔）。同源 Nginx 部署不需要添加跨域来源。

首次构建需要下载镜像、Maven 和 npm 依赖，后续构建会使用缓存。网络正常时，可以预拉取基础镜像再构建。2026-09-10 本机 Docker Hub 认证接口超时，下面的原始构建方式当前不可用，应使用下一节的替代路径：

```powershell
docker pull maven:3.9.9-eclipse-temurin-17
docker pull eclipse-temurin:17-jre-alpine
docker pull node:22-alpine
docker pull nginx:1.28-alpine
docker compose build backend frontend
docker compose up -d --no-build --wait --wait-timeout 300
```

### Docker Hub 不可达时构建前后端

2026-09-10 已实测以下路径成功：本机 Java 17 / Maven 打包后端，本机 Node 构建前端，再从 AWS ECR 的 Docker 官方镜像库下载 Java 17 和 Nginx 运行镜像。前后端 Dockerfile.prebuilt 保留原有非 root 后端用户、Nginx 代理和健康检查兼容性，不需要下载 Maven、Node 构建镜像。

在**项目根目录**执行；命令要求 Java 17、Maven、Node/npm、Docker 可用。也可以用 IDEA 的 Maven 面板对后端执行 `package`。已有 `frontend/node_modules` 时无需重复安装依赖：

```powershell
mvn -f backend/pom.xml package -Dmaven.test.skip=true
npm --prefix frontend ci
$env:VITE_API_BASE = '/miniagent'
npm --prefix frontend run build
docker build -f backend/Dockerfile.prebuilt -t miniagent-backend:local backend/zflow-app/target
docker build -f frontend/Dockerfile.prebuilt -t miniagent-frontend:local frontend
docker compose -f backend/docs/docker/docker-compose.yml -p miniagent up -d --no-build --pull never --wait --wait-timeout 300 frontend
```

后端构建上下文必须是 `backend/zflow-app/target`；前端使用单独的 `Dockerfile.prebuilt.dockerignore`，只发送 `dist` 和 Nginx 配置。改变 Java/Vue 源码后，需重新打包对应产物并重建对应镜像；已有镜像不会自动包含新的源码。

目前四个中间件镜像也已缓存在本机，新机器仍需准备它们。`--pull never` 不会下载缺少的镜像。原有多阶段 Dockerfile 仍保留；对 Compose 使用 `--build` 会走原始 Docker Hub 源码构建，不会自动选择 `Dockerfile.prebuilt`。

实际验证：六个基础服务均健康，Nginx 到后端健康接口返回 `UP`，8 个 SPA 路由和静态资源通过，浏览器能显示 ZFlow 欢迎页。另修正了 Nginx 绝对跳转丢失宿主机 `18080` 端口的问题。邮件 MCP 也保持运行；未在本轮验证真实模型调用或发送邮件。

## 服务与端口

Compose 项目名为 `miniagent`，容器、网络和数据卷与本机已有的 `dev-ops` 项目隔离。MySQL 和 Redis 使用不同宿主机端口。

| 服务 | 宿主机端口 | 容器间地址 |
| --- | --- | --- |
| 前端 / Nginx | 127.0.0.1:18080，变量 `WEB_PORT` | frontend:80 |
| 后端 | 不映射到宿主机 | backend:8066 |
| MySQL | 13306，变量 `MYSQL_PORT` | mysql:3306 |
| PostgreSQL + pgvector | 5432，变量 `POSTGRESQL_PORT` | pgvector:5432 |
| Redis | 16380，变量 `REDIS_PORT` | redis:6379 |
| RabbitMQ | 5672，变量 `RABBITMQ_PORT` | rabbitmq:5672 |
| RabbitMQ 管理页 | 15672，变量 `RABBITMQ_MANAGEMENT_PORT` | rabbitmq:15672 |
| OneAPI（可选） | 3000，变量 `ONEAPI_PORT` | one-api:3000 |
| MCP（可选） | 9001–9005 | 各服务名及对应端口 |

容器间始终使用服务名和内部端口，不使用宿主机端口 13306、16380，也不使用 localhost。若需要另一套部署，可以用 `docker compose -p 自定义名称`，同时更改映射端口，并在后续命令中持续使用相同项目名。

## 数据库初始化

MySQL 新数据卷首次启动时自动创建 `ai-agent` 数据库、20 张业务表和默认顾问 `advisor_work_memory`，并授权普通数据库用户访问该库。SQL 位于 [../mysql](../mysql/README.md)。

PostgreSQL 新数据卷首次启动时执行 `conf/table.sql`，创建 vector/pgcrypto 扩展和 `public.vector_store_openai`。向量列为 1024 维；后端显式使用这一维度，启动时无需请求模型来推导维度。

Windows 挂载文件可能被标记为所有人可写，MySQL 会忽略这类配置文件。当前启动命令先将 `my.cnf` 复制进容器并设置 0644 权限，再启动 MySQL。后端 Docker 配置同时启用 MySQL 公钥获取，以连接本项目使用 caching_sha2_password 的本地数据库。

查看库表：

```powershell
docker compose exec mysql mysql -uroot -p
```

输入本目录 `.env` 中的 `PASSWORD`，然后执行：

```sql
USE `ai-agent`;
SHOW TABLES;
SELECT advisor_id, advisor_type, advisor_param FROM ai_advisor;
```

初始化脚本只在空数据卷上自动运行。重复导入 `CREATE TABLE IF NOT EXISTS` 不会更新已有表结构，也不应通过删除卷触发升级。已有数据库需先备份并单独审查迁移。修改 `.env` 密码不会修改旧卷内已经创建的数据库账户。

Redis 为本项目独立实例：后端启动会调用 `flushDb()` 清空所选 Redis database，不能改成其他项目正在使用的 Redis database。

## 配置模型和 OSS

当前网页、账号和会话功能可在未配置模型服务时运行。以下功能需要额外配置：

- 普通聊天：在用户“设置”中添加可用的 OpenAI 兼容 API、令牌及模型，系统会创建对应 Chat Client。
- Studio 自动创建 Agent：填写 `SYSTEM_MODEL_BASE_URL`、`SYSTEM_MODEL_API_KEY`、`SYSTEM_MODEL_NAME`。
- RAG：填写 `OPENAI_BASE_URL`、`OPENAI_API_KEY` 和 `EMBEDDING_MODEL`；模型输出须支持 1024 维。改变维度需要同步修改表并重新生成旧向量。
- 头像上传：填写 `OSS_ENDPOINT`、`OSS_BUCKET`、`OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`。现有头像 URL 拼接逻辑要求 endpoint 使用不带 `https://` 的主机名，例如 `oss-cn-hangzhou.aliyuncs.com`。

修改本目录 `.env` 后重新创建后端以加载环境变量：

```powershell
docker compose up -d --no-build --wait backend
```

`not-configured` 只是启动占位值，不是可用凭据；本次没有验证真实 AI 回答、RAG 模型调用或 OSS 上传。

### 本机配置 OpenAI 兼容网关

该中转站的模型接口实际位于 `https://llm-gateway.mlamp.cn/v1`，基础地址填写
`https://llm-gateway.mlamp.cn/`，不包含 `api` 或 `v1`。
项目会另外拼接 `v1/chat/completions` 或 `/v1/embeddings`。
2026-09-08 实测：`/api/v1/chat/completions` 返回 404 Invalid URL；
`/v1/models` 和 `/v1/chat/completions` 使用本机已配置 Key 均返回 200，`gpt-5.1` 成功生成回复。
修正配置并重新创建后端后，项目自身的 `/ai/chat/complete` 和 `/ai/chat/stream` 也均成功返回 `OK`；验证用临时账号、模型及消息已清理。
此前只核对了地址拼接方式，没有验证该站的实际路由；应以这里的实测路径为准。

**普通聊天**：在网页“设置”中新增个人 MODEL，填写：

| 字段 | 值 |
| --- | --- |
| 接口地址 | `https://llm-gateway.mlamp.cn/` |
| 补全路径 | `/v1/chat/completions` |
| 模型类别 | `chat` |
| 模型名称 | 网关实际支持的模型 ID，不能填写自定义显示名称 |
| 接口密钥 | 自己的 API Key 原文，不加 `Bearer ` 前缀 |

保存后，在聊天页选择该模型。这里的配置保存在本机 Docker 的 MySQL 数据卷中，不写入源码；当前实现会保存真实 Key，不是 `.env` 引用。仅修改 `.env` 不会自动新增聊天模型。

**Studio 系统模型**：编辑本目录的 `.env`（不要覆盖原有数据库密码和 JWT_SECRET）：

```dotenv
SYSTEM_MODEL_BASE_URL=https://llm-gateway.mlamp.cn/
SYSTEM_MODEL_API_KEY='替换成自己的API Key'
SYSTEM_MODEL_NAME=替换成网关支持的模型ID
```

**RAG 向量模型**：仅在网关提供兼容的 Embeddings 接口时填写：

```dotenv
OPENAI_BASE_URL=https://llm-gateway.mlamp.cn/
OPENAI_API_KEY='替换成自己的API Key'
EMBEDDING_MODEL=替换成支持1024维输出的向量模型ID
```

聊天模型可用不代表支持向量化；`text-embedding-v4` 和 `gpt-4o-mini` 只是配置示例，不代表该网关一定提供。头像上传仍单独使用 OSS 凭据。

`.env` 已被 `.gitignore` 和后端 `.dockerignore` 排除。真实 Key 只在本机填写，不要填进 `.env.example`。保存后在本目录执行 `docker compose up -d --no-build --wait backend`，无需重建镜像；单纯 `docker compose restart` 不会加载修改后的环境变量。

## OneAPI 与 MCP

OneAPI 在 `oneapi` profile 中。若使用它，先在本项目 MySQL root 会话中创建独立数据库并授权；用户名应与 `MINIAGENT_USER` 一致：

```sql
CREATE DATABASE IF NOT EXISTS oneapi CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON oneapi.* TO 'miniagent'@'%';
```

```powershell
docker compose up -d one-api
```

访问 <http://localhost:3000> 完成渠道和令牌配置，后端模型地址改为 `http://one-api:3000`。只启动 OneAPI 不会自动得到可用模型。

MCP 在 `mcp` profile 中，可显式启动单个服务：

```powershell
docker compose up -d --build mcp-server-bocha mcp-server-amap
```

MCP 镜像现在从本仓库源码构建，镜像名为 `zflow-mcp-server-服务名:local`。五个服务配置了 `pull_policy: never`，不会去 Docker Hub 查找这些本地镜像；普通 `up` 复用已有镜像，缺少镜像时仍会构建。修改源码后需显式重新构建。也可在 Git Bash 中运行 `bash mcp/docker-build.sh`（从项目根目录），构建全部五个本地镜像；脚本不会推送到远程仓库。

应用中还需添加 MCP 连接配置和对应服务凭据。容器内 MCP 地址使用服务名，如 `http://mcp-server-bocha:9005`。

### Docker Hub 超时或 EOF：使用本机 JAR 构建

如果报错指向 `auth.docker.io/token`，并在下载 `maven`、`eclipse-temurin` 基础镜像时超时，构建尚未执行 Java 编译。这是镜像仓库连接问题；`pull_policy: never` 只阻止拉取本地成品镜像，不能解决 Dockerfile 的基础镜像下载。

2026-09-10 本机实测 Docker Hub 认证接口不可达，微软官方容器仓库可达。已通过以下替代路径构建并启动邮件 MCP：本机 Maven 打包，再使用 `mcr.microsoft.com/openjdk/jdk:17-ubuntu` 运行 JAR。原有多阶段 Dockerfile 保留，网络恢复后仍可使用。

以下命令从**项目根目录**执行。先准备本机 Java 17 和 Maven，也可以在 IDEA Maven 面板执行邮件模块的 `package`：

```powershell
mvn -f mcp/mcp-server-email/pom.xml package -Dmaven.test.skip=true
docker build --file mcp/Dockerfile.prebuilt --build-arg JAR_FILE=mcp-server-email.jar --tag zflow-mcp-server-email:local mcp/mcp-server-email/target
docker compose -f backend/docs/docker/docker-compose.yml -p miniagent up -d --no-build --pull never mcp-server-email
```

`Dockerfile.prebuilt` 的构建上下文必须是该模块的 `target` 目录；它只复制指定的可执行 JAR。更改源码后必须重新执行 Maven 打包和镜像构建，已有 JAR 不会自动更新。

本机邮件镜像已构建完成，日常启动可直接使用以下 PowerShell 命令；包含空格的路径必须加引号：

```powershell
& 'C:\Program Files\Docker\Docker\resources\bin\docker.exe' compose -f 'D:\IdeaProject\MiniAgent-main (1)\MiniAgent-main\backend\docs\docker\docker-compose.yml' -p miniagent up -d --no-build --pull never mcp-server-email
```

当前容器为 `miniagent-mcp-server-email-1`，SSE 地址为 `http://localhost:9004/sse`。已验证 SSE、MCP 初始化和 `tools/list` 返回 `sendEmail`，未调用发送工具。在 Docker Hub 连接恢复前，不要对这个预构建镜像使用 `--build`；这会重新执行原有 Dockerfile，再次访问 Docker Hub。

## 管理员与日常维护

用户先通过网页注册。如需管理后台，对自己注册的确切用户名设置管理员角色，然后重新登录：

```sql
UPDATE `ai-agent`.ai_user SET user_role='admin' WHERE user_name='替换为自己的用户名';
```

管理员登录页：<http://localhost:18080/miniagent/admin/login>。没有预置管理员或模型密钥。

```powershell
# 查看日志
docker compose logs -f --tail 100 backend frontend

# 停止本项目基础应用；数据卷保留
docker compose stop frontend backend mysql pgvector redis rabbitmq

# 再次启动（镜像已构建）
docker compose up -d --no-build --wait
```

## 验证与已知限制

- Java 17 / Maven 的 7 个模块已成功编译打包；构建跳过了仓库原有测试。
- Vue / Vite 生产构建成功，已生成 Nginx 镜像。
- MySQL 已实际导入 20 张表和默认顾问；点赞去重、统计累加及 PostgreSQL 1024 维向量写入通过事务测试，测试数据已回滚。
- 应用健康检查、账号与浏览器验证结果见 [启动验证记录](RUN-VERIFICATION.md)。
- 之前发现的首次发布模板 ID 不一致、管理端模型必填校验不足，仍属于原有业务问题，详见 [库表设计说明](../mysql/README.md)。这些问题不阻止基础服务启动，但相应功能需要后续修复。
