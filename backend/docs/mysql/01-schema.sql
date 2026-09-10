-- ZFlow MySQL 8.0 业务库初始化（依据当前 PO、Mapper 和 Repository 反推）。
-- 仅创建缺失的库表，不删除或重建现有表；不是既有数据库的迁移脚本。
-- 业务关系由应用维护，暂不添加物理外键，以兼容当前删除顺序及 owner=0。
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `ai-agent`
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `ai-agent`;

CREATE TABLE IF NOT EXISTS ai_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    user_name VARCHAR(64) NOT NULL COMMENT '登录用户名',
    password VARCHAR(255) NOT NULL COMMENT 'PasswordEncoder 生成的密码哈希',
    user_role VARCHAR(32) NOT NULL DEFAULT 'account' COMMENT 'account/admin',
    user_avatar VARCHAR(1024) NULL COMMENT '头像对象键或地址',
    user_status TINYINT NOT NULL DEFAULT 1 COMMENT '0 禁用，1 启用',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_name (user_name),
    KEY idx_user_role_updated (user_role, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账户';

CREATE TABLE IF NOT EXISTS ai_api (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    api_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '接口业务 ID',
    api_base_url VARCHAR(1024) NOT NULL COMMENT 'OpenAI 兼容接口基础地址',
    api_key TEXT NULL COMMENT '接口密钥，由应用写入，不在种子脚本中预置',
    api_completions_path VARCHAR(512) NULL DEFAULT 'v1/chat/completions' COMMENT '对话路径',
    api_embeddings_path VARCHAR(512) NULL DEFAULT 'v1/embeddings' COMMENT '向量路径',
    api_from BIGINT NOT NULL DEFAULT 0 COMMENT '0 系统配置，其他值为 ai_user.id',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_id (api_id),
    KEY idx_api_owner_created (api_from, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模型 API 接入配置';

CREATE TABLE IF NOT EXISTS ai_model (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    model_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '模型业务 ID',
    api_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '关联 ai_api.api_id',
    model_name VARCHAR(255) NOT NULL COMMENT '供应商模型名称',
    model_type VARCHAR(32) NOT NULL COMMENT '模型类型，按应用枚举写入',
    model_from BIGINT NOT NULL DEFAULT 0 COMMENT '0 系统配置，其他值为 ai_user.id',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_model_id (model_id),
    KEY idx_model_api (api_id),
    KEY idx_model_owner_updated (model_from, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='API 下的模型配置';

CREATE TABLE IF NOT EXISTS ai_advisor (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    advisor_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '顾问业务 ID',
    advisor_name VARCHAR(255) NOT NULL COMMENT '顾问名称',
    advisor_type VARCHAR(32) NOT NULL COMMENT 'Memory/Rag，大小写与代码一致',
    advisor_param LONGTEXT NULL COMMENT 'JSON 字符串：maxMessages 或 topK/filterExpression',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_advisor_id (advisor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对话顾问配置';

CREATE TABLE IF NOT EXISTS ai_prompt (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    prompt_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '提示词业务 ID',
    prompt_name VARCHAR(255) NOT NULL COMMENT '提示词名称',
    systen_prompt LONGTEXT NOT NULL COMMENT '兼容现有 PO/Mapper 的 systen 拼写',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_prompt_id (prompt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统提示词配置';

CREATE TABLE IF NOT EXISTS ai_mcp (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    mcp_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '工具业务 ID',
    mcp_name VARCHAR(255) NOT NULL COMMENT '工具名称',
    mcp_type VARCHAR(32) NOT NULL COMMENT '传输类型，按应用枚举写入',
    mcp_param LONGTEXT NOT NULL COMMENT '连接参数 JSON 字符串',
    mcp_secret LONGTEXT NULL COMMENT '用户工具凭据 JSON 字符串',
    mcp_desc TEXT NULL COMMENT '工具描述',
    mcp_timeout INT NOT NULL DEFAULT 180 COMMENT '请求超时秒数',
    mcp_from BIGINT NOT NULL DEFAULT 0 COMMENT '0 系统配置，其他值为 ai_user.id',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mcp_id (mcp_id),
    KEY idx_mcp_owner_updated (mcp_from, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MCP 工具配置';

CREATE TABLE IF NOT EXISTS ai_client (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    client_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '客户端业务 ID',
    client_type VARCHAR(32) NOT NULL COMMENT 'chat/work',
    client_role VARCHAR(64) NULL COMMENT '工作流角色，普通 chat 可为空',
    model_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '关联 ai_model.model_id',
    model_name VARCHAR(255) NULL COMMENT '模型名称冗余字段',
    client_name VARCHAR(255) NOT NULL COMMENT '客户端名称',
    client_status TINYINT NOT NULL DEFAULT 1 COMMENT '0 禁用，1 启用',
    client_from BIGINT NOT NULL DEFAULT 0 COMMENT '0 系统配置，其他值为 ai_user.id',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_client_id (client_id),
    KEY idx_client_model (model_id),
    KEY idx_client_owner_type (client_from, client_type, client_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对话及工作流客户端';

CREATE TABLE IF NOT EXISTS ai_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    client_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '关联 ai_client.client_id',
    config_type VARCHAR(32) NOT NULL COMMENT 'prompt/advisor/mcp',
    config_value VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '由 config_type 决定目标表的业务 ID',
    config_status TINYINT NOT NULL DEFAULT 1 COMMENT '0 禁用，1 启用',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_binding (client_id, config_type, config_value),
    KEY idx_config_target (config_type, config_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户端与提示词、顾问、工具的多态绑定';

CREATE TABLE IF NOT EXISTS ai_agent (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    agent_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '智能体业务 ID',
    agent_name VARCHAR(255) NOT NULL COMMENT '智能体名称',
    agent_type VARCHAR(32) NOT NULL COMMENT 'loop/step/react',
    agent_desc TEXT NULL COMMENT '智能体描述',
    model_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '关联 ai_model.model_id',
    template_id VARCHAR(128) COLLATE utf8mb4_bin NULL COMMENT '发布模板 ID；未发布时为空，Fork 来源以 ai_repo 为准',
    agent_status TINYINT NOT NULL DEFAULT 1 COMMENT '0 禁用，1 启用',
    agent_from BIGINT NOT NULL DEFAULT 0 COMMENT '0 系统配置，其他值为 ai_user.id',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_id (agent_id),
    KEY idx_agent_model (model_id),
    KEY idx_agent_template (template_id),
    KEY idx_agent_owner_updated (agent_from, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能体配置';

CREATE TABLE IF NOT EXISTS ai_flow (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    agent_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '关联 ai_agent.agent_id',
    client_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '关联 ai_client.client_id',
    client_role VARCHAR(64) NOT NULL COMMENT 'Agent 内角色标识',
    user_prompt LONGTEXT NULL COMMENT '该工作流节点的用户提示词',
    flow_seq INT NOT NULL DEFAULT 1 COMMENT '节点顺序，不对顺序值施加唯一约束',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_flow_agent_role (agent_id, client_role),
    UNIQUE KEY uk_flow_agent_client (agent_id, client_id),
    KEY idx_flow_client (client_id),
    KEY idx_flow_order (agent_id, flow_seq, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能体角色与客户端工作流绑定';

CREATE TABLE IF NOT EXISTS ai_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    task_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '任务业务 ID',
    agent_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '关联 ai_agent.agent_id',
    task_cron VARCHAR(128) NOT NULL COMMENT 'Spring Cron 表达式',
    task_desc TEXT NULL COMMENT '任务说明',
    task_param LONGTEXT NULL COMMENT '任务参数 JSON 字符串',
    task_status TINYINT NOT NULL DEFAULT 1 COMMENT '0 禁用，1 启用；与 SettingTaskDTO 默认值一致',
    task_from BIGINT NOT NULL DEFAULT 0 COMMENT '0 系统配置，其他值为 ai_user.id',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id (task_id),
    KEY idx_task_agent (agent_id),
    KEY idx_task_status (task_status),
    KEY idx_task_owner_updated (task_from, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能体定时任务';

CREATE TABLE IF NOT EXISTS ai_session (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    session_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '会话业务 ID',
    user_id BIGINT NOT NULL COMMENT '关联 ai_user.id',
    session_title VARCHAR(512) NULL COMMENT '会话标题',
    session_type VARCHAR(32) NOT NULL COMMENT 'chat/work',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_id (session_id),
    KEY idx_session_user_updated (user_id, update_time, id),
    KEY idx_session_user_type_updated (user_id, session_type, update_time, id),
    KEY idx_session_type (session_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户会话';

CREATE TABLE IF NOT EXISTS ai_message (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    session_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '关联 ai_session.session_id',
    message_content LONGTEXT NOT NULL COMMENT '消息正文或 SSE 结构化内容',
    message_role VARCHAR(32) NOT NULL COMMENT 'user/assistant',
    message_type VARCHAR(32) NOT NULL COMMENT 'chat/work-sse/work-answer',
    message_seq INT NOT NULL COMMENT '应用按 session_id + message_type 分配序号',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    KEY idx_message_session_type_seq (session_id, message_type, message_seq, create_time),
    KEY idx_message_created (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会话消息和工作流输出';

CREATE TABLE IF NOT EXISTS ai_template (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    template_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '模板业务 ID',
    user_id BIGINT NOT NULL COMMENT '发布者 ai_user.id',
    agent_name VARCHAR(255) NOT NULL COMMENT '快照中的智能体名称',
    agent_type VARCHAR(32) NOT NULL COMMENT 'loop/step/react',
    agent_desc TEXT NULL COMMENT '智能体描述快照',
    api_base_url VARCHAR(1024) NOT NULL COMMENT '接口基础地址快照',
    api_completion_url VARCHAR(512) NULL COMMENT '对话路径快照，对应 api_completions_path',
    model_name VARCHAR(255) NOT NULL COMMENT '模型名称快照',
    model_type VARCHAR(32) NOT NULL COMMENT '模型类型快照',
    snapshot LONGTEXT NOT NULL COMMENT 'SnapshotView JSON 字符串，Fork 重建配置的依据',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_id (template_id),
    KEY idx_template_user_updated (user_id, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='已发布智能体快照';

CREATE TABLE IF NOT EXISTS ai_repo (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    repo_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '仓库条目业务 ID',
    user_id BIGINT NOT NULL COMMENT '关联 ai_user.id',
    agent_id VARCHAR(128) COLLATE utf8mb4_bin NULL COMMENT 'self/fork 关联智能体；favor 可为空',
    template_id VARCHAR(128) COLLATE utf8mb4_bin NULL COMMENT 'fork/favor 关联模板；未发布 self 可为空',
    repo_type VARCHAR(32) NOT NULL COMMENT 'self/fork/favor',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_repo_id (repo_id),
    UNIQUE KEY uk_repo_user_agent_type (user_id, agent_id, repo_type),
    UNIQUE KEY uk_repo_user_template_type (user_id, template_id, repo_type),
    KEY idx_repo_user_updated (user_id, update_time, id),
    KEY idx_repo_agent (agent_id),
    KEY idx_repo_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户的自建、Fork、收藏仓库';

CREATE TABLE IF NOT EXISTS ai_plaza (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    plaza_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '广场条目业务 ID',
    template_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '关联 ai_template.template_id',
    user_id BIGINT NOT NULL COMMENT '发布者 ai_user.id',
    agent_name VARCHAR(255) NOT NULL COMMENT '展示名称冗余字段',
    agent_type VARCHAR(32) NOT NULL COMMENT '智能体策略类型',
    plaza_title VARCHAR(255) NOT NULL COMMENT '发布标题',
    plaza_desc TEXT NULL COMMENT '发布说明',
    like_count INT NOT NULL DEFAULT 0 COMMENT '点赞计数，应用维护',
    favor_count INT NOT NULL DEFAULT 0 COMMENT '收藏计数，应用维护',
    comment_count INT NOT NULL DEFAULT 0 COMMENT '评论计数，应用维护',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_plaza_id (plaza_id),
    KEY idx_plaza_template (template_id, id),
    KEY idx_plaza_user (user_id),
    KEY idx_plaza_created (create_time, id),
    KEY idx_plaza_likes (like_count, id),
    KEY idx_plaza_favors (favor_count, id),
    KEY idx_plaza_comments (comment_count, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模板广场发布记录';

CREATE TABLE IF NOT EXISTS ai_plaza_like (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    plaza_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '关联 ai_plaza.plaza_id',
    user_id BIGINT NOT NULL COMMENT '关联 ai_user.id',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_like_plaza_user (plaza_id, user_id),
    KEY idx_like_user_plaza (user_id, plaza_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户对广场条目的点赞';

CREATE TABLE IF NOT EXISTS ai_plaza_favor (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    plaza_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '关联 ai_plaza.plaza_id',
    user_id BIGINT NOT NULL COMMENT '关联 ai_user.id',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_favor_plaza_user (plaza_id, user_id),
    KEY idx_favor_user_plaza (user_id, plaza_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户对广场条目的收藏';

CREATE TABLE IF NOT EXISTS ai_plaza_comment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    comment_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '评论业务 ID',
    plaza_id VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '关联 ai_plaza.plaza_id',
    user_id BIGINT NOT NULL COMMENT '关联 ai_user.id',
    user_name VARCHAR(64) NOT NULL COMMENT '评论时用户名快照',
    comment_content LONGTEXT NOT NULL COMMENT '评论正文',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_comment_id (comment_id),
    KEY idx_comment_plaza_created (plaza_id, create_time, id),
    KEY idx_comment_user_plaza (user_id, plaza_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='广场评论';

CREATE TABLE IF NOT EXISTS ai_stat (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    stat_date DATE NOT NULL COMMENT '统计日期',
    stat_category VARCHAR(32) COLLATE utf8mb4_bin NOT NULL COMMENT '统计类别',
    stat_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '统计维度键',
    stat_value VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT '统计维度值',
    stat_count INT NOT NULL DEFAULT 0 COMMENT '累计次数，对应 Java Integer',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stat_daily_dimension (stat_date, stat_category, stat_key, stat_value),
    KEY idx_stat_category_key_value (stat_category, stat_key, stat_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='按日与统计维度聚合的计数';
