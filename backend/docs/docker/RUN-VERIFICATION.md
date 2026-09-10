# 启动验证记录

本文件为 2026-09-08 的历史记录。改名后的当前验证结果见 [ZFlow 验证记录](../ZFLOW-VERIFICATION.md)。

验证日期：2026-09-08。环境：Windows / Docker Desktop Linux 容器。

## 运行入口

- 网页：http://localhost:18080/miniagent/
- 注册：http://localhost:18080/miniagent/register
- 管理员登录：http://localhost:18080/miniagent/admin/login
- 健康接口：http://localhost:18080/miniagent/api/v1/actuator/health

本项目 frontend、backend、mysql、pgvector、redis、rabbitmq 均已运行并通过 Docker 健康检查。用户先前启动的 mcp-server-bocha 保持运行。没有修改或停止已有 dev-ops 项目的容器。

## 已通过的检查

| 检查 | 结果 |
| --- | --- |
| 后端构建 | Java 17，Maven 7 个模块 BUILD SUCCESS，可执行 JAR 已打入镜像 |
| 前端构建 | Vue / Vite 生产构建成功，Nginx 静态资源镜像生成 |
| MySQL 初始化 | 20 张业务表、默认 Memory 顾问已导入 |
| MySQL 配置加载 | 容器文件权限修正后，time_zone 为 +08:00 |
| 点赞唯一键 | 事务内重复 INSERT IGNORE 后仅 1 行 |
| 日统计聚合 | 同维度两次 upsert 后仅 1 行，累计值为 2 |
| pgvector | 向量列为 vector(1024)，1024 维插入成功 |
| Nginx / 后端健康接口 | HTTP 200，status=UP |
| 未登录鉴权 | 受保护的会话接口返回 HTTP 401 |
| SPA 深层路由 | 直接访问 /miniagent/register 返回页面 |
| 用户功能 | 注册、登录、JWT 和个人资料查询通过 |
| 会话功能 | 创建、列表、改名、删除通过 |
| Edge 浏览器 | 登录页渲染、表单登录、跳转聊天页通过，无页面脚本异常或同源 HTTP 5xx |

数据库约束测试写入均已回滚，浏览器/API 验证使用的临时账号及会话已删除。未预置管理员密码。用户可自行在网页注册账号。

## 启动过程中处理的问题

- BuildKit 连接 Docker Hub 认证地址超时：先通过 Docker 引擎拉取基础镜像，再使用本地镜像构建成功。
- Windows 挂载的 my.cnf 被 MySQL 视为所有人可写而忽略：改为复制到容器内并设置 0644 权限后启动。
- Docker 内连接使用 mysql/pgvector/redis/rabbitmq 服务名及内部端口；与宿主机已有 MySQL、Redis 分离。
- 明确配置 PgVectorStore 的 1024 维，避免启动依赖在线模型推导维度。
- 新增 Actuator 健康接口及相应匿名访问规则，使前端等待后端就绪后再启动。

## 尚未验证的能力

实际模型调用、Agent 执行、RAG 模型服务、OSS 上传及第三方 MCP 工具调用未验证，因为没有提供真实 API/OSS 凭据。环境中的 not-configured 仅支持基础启动，不表示这些功能已经可用。

构建跳过仓库原有测试；本记录中的验证是数据库事务测试、HTTP 冒烟和真实浏览器检查，不代表完整业务回归。此前发现的发布模板 ID 不一致、管理端 Agent 模型校验不足仍记录在库表设计说明中，本次没有改动这些业务逻辑。

配置及重启方法见 [Docker 运行说明](README.md)。
