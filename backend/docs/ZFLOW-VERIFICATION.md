# ZFlow 改名与运行验证

验证日期：2026-09-09。

追加验证：2026-09-10 邮件 MCP、前后端容器启动问题已处理，结果见下节。

## 结论

工程包名、Maven 模块、启动入口和当前页面品牌已统一为 ZFlow。基础业务已在真实 MySQL、PostgreSQL/pgvector、Redis、RabbitMQ 环境中运行通过；这不代表所有 AI 业务均已验证。

## 2026-09-10 邮件 MCP 容器追加验证

- Docker Hub 认证接口实测连接超时；微软官方容器仓库 `/v2/` 返回 HTTP 200。
- 五个 MCP 服务增加 `pull_policy: never`，本地 `zflow-*:local` 标签不再尝试远程拉取。Compose 配置校验通过；用缺少镜像的博查服务进行 dry-run，确认仍会安排源码构建，未实际启动博查容器。
- 邮件模块本机 Maven 打包成功（跳过测试）；新增 `mcp/Dockerfile.prebuilt`，使用微软官方 OpenJDK 17 镜像和已打包 JAR，实际 Docker 构建成功。
- 使用原项目名 `miniagent` 执行 `compose up -d mcp-server-email` 成功，容器 `miniagent-mcp-server-email-1` 持续运行，映射端口 9004。
- 实际验证 `http://127.0.0.1:9004/sse`、MCP `initialize`、`tools/list` 均成功，发现 `sendEmail`；没有调用发送工具。
- 本次保留邮件服务容器及 `miniagent_ai-agent` 网络供开发使用。结果保存在 `.tmp/verify-email-docker-result.json`，重建及启动命令见 [Docker 部署说明](docker/README.md#docker-hub-超时或-eof使用本机-jar-构建)。

本节覆盖邮件 MCP 的预构建 JAR 容器；前后端容器结果见下一节。Docker Hub 连通性和对外发送邮件仍未验证通过。

## 2026-09-10 前后端容器追加验证

- Compose 前后端也增加 `pull_policy: never`，解决本地 `miniagent-frontend:local`、`miniagent-backend:local` 标签被当成远程镜像拉取的问题。
- 后端 7 个 Maven 项目打包成功（跳过测试）；前端生产构建及 8 个 HTTP 入口冒烟检查通过。
- 从 AWS ECR 的 Docker 官方镜像库实际拉取 `nginx:1.28-alpine` 和 `eclipse-temurin:17-jre-alpine`。新增前后端 `Dockerfile.prebuilt`，使用本机产物实际构建两份应用镜像成功。
- 原 `miniagent` Compose 项目的前端、后端、MySQL、pgvector、Redis、RabbitMQ 均实际启动并健康；邮件 MCP 保持运行。服务及四个具名数据卷保留供开发使用，未删除用户自行启动的其他容器。
- 修复 Nginx 将外部 `18080` 端口丢失的绝对跳转，改用相对跳转；验证 `/`、`/miniagent` 的跳转目标保留浏览器端口。
- 实际容器验证：8 个 SPA 路由、ZFlow 页面标题、JS/CSS 响应、缺失资源返回 404、Nginx 代理后端健康接口返回 200 / UP、未登录访问受保护接口返回 401。
- 浏览器打开 `http://localhost:18080/miniagent/`，实际显示 ZFlow 欢迎页。该浏览器已有登录状态，本轮未重新测试注册登录或修改业务数据。
- 构建日志位于 `.tmp/backend-prebuilt-package.log`；部署检查结果位于 `.tmp/verify-deployed-web-result.json`。重建步骤见 [Docker 部署说明](docker/README.md)。

前后端预构建容器已验证通过，取代早期记录中“完整应用容器尚未启动”的限制；原多阶段 Dockerfile 的 Docker Hub 下载问题依然存在。本轮未验证真实模型、Agent、OSS 或外部 MCP 工具调用。

## 本轮修正

- 欢迎页原先将 `Mini` 和 `Agent` 拆成两个标题，现统一为 `ZFlow` 和 `知流`。
- `ZFlowApplication` 将命令行参数传入 Spring Boot，已用非默认端口 28066 验证生效。
- 跨域来源移除原作者域名，改为可配置的 `ZFLOW_WEB_ALLOWED_ORIGIN_PATTERNS`，默认仅允许本机开发来源。
- Compose 中五个 MCP 服务改为构建本仓库源码，不再拉取原作者发布的旧镜像。
- MCP 构建脚本改为构建本地 `zflow-mcp-server-*:local` 镜像；修正高德、企业微信和博查 Dockerfile 的端口声明。
- 补齐 `frontend/scripts/test-routes.mjs`，使 `npm test` 能执行生产构建和真实 HTTP 入口检查。

## 验证结果

| 项目 | 结果与范围 |
| --- | --- |
| Java 命名 | 375 个 Java 文件的包声明、目录以及 Maven 启动类一致；20 个 MyBatis XML 的类引用有效 |
| 后端构建 | 六个模块及父 POM 打包成功；使用 Java 17，构建跳过单元测试 |
| MCP 构建与启动 | 五个独立服务的 JAR 已构建并在 29001–29005 本地测试端口启动 |
| MyBatis | 实际解析 20 个 XML、182 条映射语句；数据库静态兼容检查通过 |
| 前端检查 | `npm test` 通过：生产构建、8 个 SPA 入口、页面标题、JS/CSS 路径和响应类型 |
| 中间件 | 独立 `zflow-verify` Compose 项目的 MySQL、pgvector、Redis、RabbitMQ 均健康 |
| 启动与健康 | 实际运行 `zflow-app.jar`，健康接口返回 HTTP 200 / UP，非默认端口参数生效 |
| 认证 | 注册、重新登录、个人资料、重复注册拒绝、错误密码拒绝、非法 JWT 返回 401 |
| 权限 | 普通账号访问管理员 API 返回 403；临时管理员重新登录后成功读取仪表盘 |
| 会话 | 创建、列表、改名、删除及消息读取通过；另一用户不能读取、改名或删除该会话 |
| 数据读取 | 模型、Agent、MCP 列表及 PostgreSQL 知识库查询通过（空配置也能正常返回） |
| 跨域 | 本机 Origin 的预检通过；原作者域名的预检返回 403 |
| MCP 协议 | 五个服务均完成 SSE 连接、initialize、tools/list；共发现 6 个工具，没有调用工具 |
| 浏览器 | 真实表单注册后进入欢迎页，创建会话后进入聊天页，退出及再次登录通过；欢迎页品牌已复核 |
| Docker 配置 | Compose 校验通过；完整应用镜像构建未通过网络下载阶段，未宣称容器化应用部署成功 |

## 仍然保留的旧标识

以下属于前一阶段明确保留的兼容设置：`/miniagent/`、`/miniagent/api/v1`、`miniagent.*` 配置键、`X-MiniAgent-*` 请求头、`ai-agent` 数据库与网络标识、Compose 项目名 `miniagent` 及容器内运行用户。原作者署名、许可证和原项目参考截图也保留。

外层工作区文件夹沿用原路径，IDEA 显示名与源码工程名为 ZFlow。原 `cover.png`、`logo.jpg` 素材没有被当前页面引用。

## 后续真实模型联调（2026-09-10）

新版前端通过同源 Vite 代理连接现有 Docker 后端。账号 222 的 gpt-5.1 聊天、step 智能体执行与 SSE 持久化、文件知识库 1024 维向量导入和实际检索已通过。系统模型与向量模型已替换占位配置；密钥仅留在本机 Docker `.env`。

实测修复了缓存先于事务提交失效、发布模板 ID 不一致、非法会话类型三个后端问题。Maven 全模块 package 成功，3 项 CacheEvictAspect 回归测试实际执行并通过；修复镜像已在原后端容器加载。广场异步发布/重复发布/收藏/点赞/Fork/删除及用户配置 CRUD 已验证。完整证据与未测范围见 [前端真实联调记录](../../frontend/docs/LIVE-INTEGRATION.md)。以下旧记录保留为前一阶段的验证边界。

## 前一阶段尚未验证

- 真实模型回答、Agent 工作流执行、RAG Embedding/检索效果：当前模型密钥为占位值，测试数据库也未配置可选模型，因此聊天页显示“暂无模型”。
- OSS 文件上传：缺少真实 OSS 配置。
- MCP 的天气、搜索、邮件、企业微信和 CSDN 实际调用：只验证协议与工具声明，没有进行对外操作。
- 后端、前端及 MCP 的完整 Docker 构建：Java 运行时镜像下载在 Docker Hub 认证阶段超时/EOF。本轮用本机 Java 17 和 Vite 连接隔离的 Docker 中间件完成业务联调。
- 新增前端检查属于 HTTP 冒烟测试；没有补建覆盖全部业务的单元测试或全量回归套件。

## 验证环境与复查

测试使用独立 Compose 项目、独立数据卷和临时账号。验证后已关闭临时前端服务、后端及五个 MCP 进程，并删除 `zflow-verify` 的四个容器、四个数据卷和网络，临时账号随测试库清理；原项目 `.env` 和业务数据未被测试覆盖。

可重复执行的源码检查：

```shell
mvn -f backend/pom.xml package -Dmaven.test.skip=true
node backend/docs/mysql/validate-schema.mjs
cd frontend
npm test
```

本工作区中的 `.tmp/verify-http-result.json`、`.tmp/verify-mcp-result.json` 和 `.tmp/verify-backend-build.log` 保存本轮结果。改名前备份仍位于 `.tmp/zflow-before-rename/`。这些临时文件不纳入版本控制。
