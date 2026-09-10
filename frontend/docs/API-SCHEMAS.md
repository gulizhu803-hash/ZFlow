# 前端接口字段字典

[返回对接指南](API.md) · [接口清单](API-ENDPOINTS.md)

依据当前 Java DTO/VO 和返回实体整理。仅收录 HTTP 接口可达的结构与任务 SSE 实体；内部用户实体和调度对象不作为响应列出。同名类型按模块区分，内部类单列，字段不能摊平。

请求表的“必填/约束”描述 Bean Validation 注解，**不是完整业务校验**；更新 ID、关联资源和 JSON 字符串另有业务规则。无注解不代表省略一定可用。默认值指字段初始化，显式 null 通常覆盖它。响应字段未声明非空契约，前端应容忍 null。Long 序列化为数字，超出 JS 安全整数时需无损解析。

NotBlank：非 null 且含非空白字符；NotNull：非 null；NotEmpty：集合至少一项，不表示每项均合法。入口标注 @Valid 时才应用对应 DTO 校验。

## 公共包装

[common.Result](#common-result) · [common.PageResult](#common-pageresult)

<a id="common-result"></a>

### common.Result

[源码](../../backend/zflow-types/src/main/java/com/zgl/zflow/types/result/Result.java#L13)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `code` | number（整数） | 未声明非空 | 业务码：200 成功、500 失败 |
| `info` | string | 未声明非空 | 业务提示 |
| `data` | T | 未声明非空 | 业务数据；Void 或错误时为 null |

<a id="common-pageresult"></a>

### common.PageResult

[源码](../../backend/zflow-types/src/main/java/com/zgl/zflow/types/result/PageResult.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `total` | number（整数） | 未声明非空 | 总记录数 |
| `pageNum` | number（整数） | 未声明非空 | 页码，从 1 开始 |
| `pageSum` | number（整数） | 未声明非空 | 总页数 |
| `pageSize` | number（整数） | 未声明非空 | 每页条数 |
| `list` | array&lt;T&gt; | 未声明非空 | 当前页记录 |

## 用户与设置

[user.AuthDTO](#user-authdto) · [user.ProfileEditDTO](#user-profileeditdto) · [user.SettingApiModelDTO](#user-settingapimodeldto) · [user.SettingMcpDTO](#user-settingmcpdto) · [user.SettingTaskDTO](#user-settingtaskdto) · [user.AuthVO](#user-authvo) · [user.QueryChatClientVO](#user-querychatclientvo) · [user.QueryMcpVO](#user-querymcpvo) · [user.QueryModelVO](#user-querymodelvo) · [user.QueryRagVO](#user-queryragvo) · [user.QueryRoleVO](#user-queryrolevo) · [user.QueryWorkAgentVO](#user-queryworkagentvo) · [user.UserApiModelVO](#user-userapimodelvo) · [user.UserMcpVO](#user-usermcpvo) · [user.UserTaskVO](#user-usertaskvo)

<a id="user-authdto"></a>

### user.AuthDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/user/model/dto/AuthDTO.java#L13)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `userName` | string | NotBlank | 用户名 |
| `password` | string | NotBlank | 密码 |

<a id="user-profileeditdto"></a>

### user.ProfileEditDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/user/model/dto/ProfileEditDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `userName` | string | NotBlank | 用户名 |
| `oldPassword` | string | 无字段注解 | 原密码；与 newPassword 配对 |
| `newPassword` | string | 无字段注解 | 新密码；与 oldPassword 配对 |

<a id="user-settingapimodeldto"></a>

### user.SettingApiModelDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/user/model/dto/SettingApiModelDTO.java#L13)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `apiId` | string | 无字段注解 | 新增可省略；更新必传原 apiId |
| `modelName` | string | NotBlank | 模型名称 |
| `modelType` | string | NotBlank | 模型类型 |
| `apiBaseUrl` | string | NotBlank | 模型服务基础地址 |
| `apiKey` | string | NotBlank | API 密钥；展示和日志中应脱敏 |
| `apiCompletionPath` | string | NotBlank | 对话路径（用户设置字段，单数 Completion） |

<a id="user-settingmcpdto"></a>

### user.SettingMcpDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/user/model/dto/SettingMcpDTO.java#L13)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `mcpId` | string | 无字段注解 | 新增可省略；更新必传原 mcpId |
| `mcpName` | string | NotBlank | MCP 名称 |
| `mcpType` | string | NotBlank | sse / stdio |
| `mcpDesc` | string | NotBlank | MCP 描述 |
| `mcpParam` | string | 无字段注解 | 配置的 JSON 字符串，格式见对接指南 |
| `mcpSecret` | string | 无字段注解 | MCP 凭据字符串；返回不保证脱敏 |

<a id="user-settingtaskdto"></a>

### user.SettingTaskDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/user/model/dto/SettingTaskDTO.java#L14)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `taskId` | string | 无字段注解 | 新增可省略；更新必传原 taskId |
| `agentId` | string | NotBlank | 智能体标识 |
| `taskCron` | string | NotBlank | Spring 六字段 Cron |
| `taskDesc` | string | NotBlank | 定时任务描述 |
| `taskParam` | string | NotBlank | JSON 字符串，含 maxRetry/maxRound/maxPace/userMessage |
| `taskStatus` | number（整数） | NotNull；默认 `1` | 0 禁用 / 1 启用 |

<a id="user-authvo"></a>

### user.AuthVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/user/model/vo/AuthVO.java#L12)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `token` | string | 未声明非空 | JWT；后续放入 Authorization 请求头 |
| `userName` | string | 未声明非空 | 用户名 |
| `userRole` | string | 未声明非空 | 用户角色（user / admin） |
| `userAvatar` | string | 未声明非空 | 头像地址 |
| `userStatus` | number（整数） | 未声明非空 | 0 禁用 / 1 启用 |

<a id="user-querychatclientvo"></a>

### user.QueryChatClientVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/user/model/vo/QueryChatClientVO.java#L12)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `clientId` | string | 未声明非空 | 客户端标识 |
| `clientName` | string | 未声明非空 | 客户端名称 |
| `modelName` | string | 未声明非空 | 模型名称 |

<a id="user-querymcpvo"></a>

### user.QueryMcpVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/user/model/vo/QueryMcpVO.java#L12)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `mcpId` | string | 未声明非空 | MCP 标识 |
| `mcpName` | string | 未声明非空 | MCP 名称 |
| `mcpDesc` | string | 未声明非空 | MCP 描述 |

<a id="user-querymodelvo"></a>

### user.QueryModelVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/user/model/vo/QueryModelVO.java#L12)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `apiId` | string | 未声明非空 | API 配置标识 |
| `modelId` | string | 未声明非空 | 模型配置标识；与 clientId 不同 |
| `modelName` | string | 未声明非空 | 模型名称 |

<a id="user-queryragvo"></a>

### user.QueryRagVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/user/model/vo/QueryRagVO.java#L12)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `ragTag` | string | 未声明非空 | 知识库标签 |

<a id="user-queryrolevo"></a>

### user.QueryRoleVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/user/model/vo/QueryRoleVO.java#L12)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `strategy` | string | 未声明非空 | step / loop / react |
| `roleName` | string | 未声明非空 | 角色名称 |
| `roleDesc` | string | 未声明非空 | 角色职责描述 |

<a id="user-queryworkagentvo"></a>

### user.QueryWorkAgentVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/user/model/vo/QueryWorkAgentVO.java#L12)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `agentId` | string | 未声明非空 | 智能体标识 |
| `agentType` | string | 未声明非空 | 策略类型（step / loop / react） |
| `agentName` | string | 未声明非空 | 智能体名称 |
| `agentDesc` | string | 未声明非空 | 智能体职责描述 |

<a id="user-userapimodelvo"></a>

### user.UserApiModelVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/user/model/vo/UserApiModelVO.java#L12)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `apiId` | string | 未声明非空 | API 配置标识 |
| `modelId` | string | 未声明非空 | 模型配置标识；与 clientId 不同 |
| `modelName` | string | 未声明非空 | 模型名称 |
| `modelType` | string | 未声明非空 | 模型类型 |
| `apiKey` | string | 未声明非空 | API 密钥；展示和日志中应脱敏 |
| `apiBaseUrl` | string | 未声明非空 | 模型服务基础地址 |
| `apiCompletionPath` | string | 未声明非空 | 对话路径（用户设置字段，单数 Completion） |

<a id="user-usermcpvo"></a>

### user.UserMcpVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/user/model/vo/UserMcpVO.java#L12)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `mcpId` | string | 未声明非空 | MCP 标识 |
| `mcpName` | string | 未声明非空 | MCP 名称 |
| `mcpType` | string | 未声明非空 | sse / stdio |
| `mcpDesc` | string | 未声明非空 | MCP 描述 |
| `mcpParam` | string | 未声明非空 | 配置的 JSON 字符串，格式见对接指南 |
| `mcpSecret` | string | 未声明非空 | MCP 凭据字符串；返回不保证脱敏 |

<a id="user-usertaskvo"></a>

### user.UserTaskVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/user/model/vo/UserTaskVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `taskId` | string | 未声明非空 | 定时任务标识 |
| `agentId` | string | 未声明非空 | 智能体标识 |
| `taskCron` | string | 未声明非空 | Spring 六字段 Cron |
| `taskDesc` | string | 未声明非空 | 定时任务描述 |
| `taskParam` | string | 未声明非空 | JSON 字符串，含 maxRetry/maxRound/maxPace/userMessage |
| `taskStatus` | number（整数） | 未声明非空 | 0 禁用 / 1 启用 |
| `updateTime` | string（ISO 日期时间） | 未声明非空 | 更新时间；LocalDateTime，无时区偏移 |

## 会话

[session.MessageVO](#session-messagevo) · [session.SessionVO](#session-sessionvo)

<a id="session-messagevo"></a>

### session.MessageVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/session/model/vo/MessageVO.java#L11)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `messageContent` | string | 未声明非空 | 消息正文；work-sse 为 JSON 字符串 |
| `messageRole` | string | 未声明非空 | user / assistant |
| `messageType` | string | 未声明非空 | chat / work-sse / work-answer |
| `messageSeq` | number（整数） | 未声明非空 | 会话、消息类型内的顺序号 |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |

<a id="session-sessionvo"></a>

### session.SessionVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/session/model/vo/SessionVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `sessionId` | string | 未声明非空 | 会话标识 |
| `userName` | string | 未声明非空 | 用户名 |
| `sessionTitle` | string | 未声明非空 | 会话标题 |
| `sessionType` | string | 未声明非空 | chat / work |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |

## AI 与流事件

[ai.AiArmoryDTO](#ai-aiarmorydto) · [ai.AiChatDTO](#ai-aichatdto) · [ai.AiUploadDTO](#ai-aiuploaddto) · [ai.AiWorkDTO](#ai-aiworkdto) · [ai.ExecuteResponseEntity](#ai-executeresponseentity)

<a id="ai-aiarmorydto"></a>

### ai.AiArmoryDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/ai/model/dto/AiArmoryDTO.java#L13)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `armoryType` | string | NotBlank | chat / work |
| `armoryId` | string | NotBlank | chat 传 clientId；work 传 agentId |

<a id="ai-aichatdto"></a>

### ai.AiChatDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/ai/model/dto/AiChatDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `clientId` | string | NotBlank | 客户端标识 |
| `userMessage` | string | NotBlank | 本次输入文本 |
| `sessionId` | string | NotBlank | 会话标识 |
| `mcpIdList` | array&lt;string&gt; | 无字段注解 | MCP 标识数组 |
| `ragTag` | string | 无字段注解 | 知识库标签 |
| `temperature` | number | 无字段注解；默认 `0.6` | 未传时 0.6；DTO 无范围校验 |
| `presencePenalty` | number | 无字段注解；默认 `0.0` | 未传时 0.0；DTO 无范围校验 |
| `maxCompletionTokens` | number（整数） | 无字段注解；默认 `8192` | 未传时 8192；DTO 无范围校验 |

<a id="ai-aiuploaddto"></a>

### ai.AiUploadDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/ai/model/dto/AiUploadDTO.java#L12)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `repoUrl` | string | 无字段注解 | Git 仓库地址 |
| `username` | string | 无字段注解 | Git 凭据用户名（小写 username） |
| `password` | string | 无字段注解 | Git 凭据密码或访问令牌 |

<a id="ai-aiworkdto"></a>

### ai.AiWorkDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/ai/model/dto/AiWorkDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `agentId` | string | NotBlank | 智能体标识 |
| `agentDesc` | string | NotBlank | 智能体职责描述 |
| `userMessage` | string | NotBlank | 本次输入文本 |
| `sessionId` | string | NotBlank | 会话标识 |
| `maxRound` | number（整数） | NotNull；Min(1) | 循环推演最大轮数；执行 DTO 必填 |
| `maxRetry` | number（整数） | NotNull；Min(1) | 分步执行重试配置；执行 DTO 必填 |
| `maxPace` | number（整数） | NotNull；Min(1) | 单步推理最大步数；执行 DTO 必填 |

<a id="ai-executeresponseentity"></a>

### ai.ExecuteResponseEntity

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/ai/model/entity/ExecuteResponseEntity.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `clientType` | string | 未声明非空 | 执行角色（如 PLANNER）；完成事件为 complete |
| `sectionType` | string | 未声明非空 | 阶段枚举，见对接指南 |
| `sectionContent` | string | 未声明非空 | 阶段内容字符串；内部可能仍是 JSON 文本 |
| `round` | number（整数） | 未声明非空 | 当前轮次，未使用时可为 null |
| `pace` | number（整数） | 未声明非空 | 当前步次，未使用时可为 null |
| `step` | number（整数） | 未声明非空 | 步骤号，未使用时可为 null |
| `timestamp` | number（整数） | 未声明非空 | Unix 毫秒时间戳 |
| `sessionId` | string | 未声明非空 | 会话标识 |

## 工作区

[workspace.AgentCreateDTO](#workspace-agentcreatedto) · [workspace.AgentPublishDTO](#workspace-agentpublishdto) · [workspace.AgentUpdateBaseDTO](#workspace-agentupdatebasedto) · [workspace.AgentUpdateMcpDTO](#workspace-agentupdatemcpdto) · [workspace.AgentUpdateModelDTO](#workspace-agentupdatemodeldto) · [workspace.AgentUpdateSystemPromptDTO](#workspace-agentupdatesystempromptdto) · [workspace.AgentUpdateUserPromptDTO](#workspace-agentupdateuserpromptdto) · [workspace.PlazaCommentAreaDTO](#workspace-plazacommentareadto) · [workspace.PlazaCommentDTO](#workspace-plazacommentdto) · [workspace.PlazaPageDTO](#workspace-plazapagedto) · [workspace.AgentVO](#workspace-agentvo) · [workspace.AgentVO.McpInfo](#workspace-agentvo-mcpinfo) · [workspace.AgentVO.ClientInfo](#workspace-agentvo-clientinfo) · [workspace.CommentVO](#workspace-commentvo) · [workspace.PlazaVO](#workspace-plazavo) · [workspace.RepoVO](#workspace-repovo) · [workspace.TemplateVO](#workspace-templatevo) · [workspace.TemplateVO.McpInfo](#workspace-templatevo-mcpinfo) · [workspace.TemplateVO.ClientInfo](#workspace-templatevo-clientinfo)

<a id="workspace-agentcreatedto"></a>

### workspace.AgentCreateDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/dto/AgentCreateDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `modelId` | string | NotBlank | 模型配置标识；与 clientId 不同 |
| `modelName` | string | NotBlank | 模型名称 |
| `mcpIdSet` | array&lt;string&gt; | 无字段注解 | MCP 标识数组，后端 Set 去重 |
| `strategy` | string | NotBlank | step / loop / react |
| `agentName` | string | NotBlank | 智能体名称 |
| `agentDesc` | string | NotBlank | 智能体职责描述 |

<a id="workspace-agentpublishdto"></a>

### workspace.AgentPublishDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/dto/AgentPublishDTO.java#L13)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `agentId` | string | NotBlank | 智能体标识 |
| `plazaTitle` | string | NotBlank | 发布标题 |
| `plazaDesc` | string | NotBlank | 发布描述 |

<a id="workspace-agentupdatebasedto"></a>

### workspace.AgentUpdateBaseDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/dto/AgentUpdateBaseDTO.java#L13)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `agentId` | string | NotBlank | 智能体标识 |
| `agentName` | string | NotBlank | 智能体名称 |
| `agentDesc` | string | NotBlank | 智能体职责描述 |

<a id="workspace-agentupdatemcpdto"></a>

### workspace.AgentUpdateMcpDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/dto/AgentUpdateMcpDTO.java#L16)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `agentId` | string | NotBlank | 智能体标识 |
| `clientIdList` | array&lt;string&gt; | NotEmpty | 要更新的客户端标识数组 |
| `mcpIdList` | array&lt;string&gt; | 无字段注解 | 实际应传数组；[] 清空，null/省略会导致遍历异常 |

<a id="workspace-agentupdatemodeldto"></a>

### workspace.AgentUpdateModelDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/dto/AgentUpdateModelDTO.java#L16)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `agentId` | string | NotBlank | 智能体标识 |
| `modelId` | string | NotBlank | 模型配置标识；与 clientId 不同 |
| `clientIdList` | array&lt;string&gt; | NotEmpty | 要更新的客户端标识数组 |

<a id="workspace-agentupdatesystempromptdto"></a>

### workspace.AgentUpdateSystemPromptDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/dto/AgentUpdateSystemPromptDTO.java#L14)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `agentId` | string | NotBlank | 智能体标识 |
| `promptId` | string | NotBlank | 系统提示词标识 |
| `systemPrompt` | string | NotBlank | 系统提示词正文 |

<a id="workspace-agentupdateuserpromptdto"></a>

### workspace.AgentUpdateUserPromptDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/dto/AgentUpdateUserPromptDTO.java#L14)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `agentId` | string | NotBlank | 智能体标识 |
| `flowId` | number（整数） | NotNull | 流程数值 ID，来自 AgentVO.clientInfoList |
| `userPrompt` | string | NotBlank | 用户提示词模板 |

<a id="workspace-plazacommentareadto"></a>

### workspace.PlazaCommentAreaDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/dto/PlazaCommentAreaDTO.java#L16)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `plazaId` | string | NotBlank | 广场条目标识 |
| `pageNum` | number（整数） | NotNull；Min(1) | 页码，从 1 开始 |
| `pageSize` | number（整数） | NotNull；Min(1)；Max(20) | 每页条数 |

<a id="workspace-plazacommentdto"></a>

### workspace.PlazaCommentDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/dto/PlazaCommentDTO.java#L13)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `plazaId` | string | NotBlank | 广场条目标识 |
| `commentContent` | string | NotBlank | 评论正文 |

<a id="workspace-plazapagedto"></a>

### workspace.PlazaPageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/dto/PlazaPageDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 无字段注解 | 查询关键词 |
| `sortBy` | string | 无字段注解 | like / favor / comment；其他值按创建时间排序 |
| `sortOrder` | string | 无字段注解 | asc 升序；其他值降序 |
| `pageNum` | number（整数） | NotNull；Min(1) | 页码，从 1 开始 |
| `pageSize` | number（整数） | NotNull；Min(1)；Max(10) | 每页条数 |

<a id="workspace-agentvo"></a>

### workspace.AgentVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/vo/AgentVO.java#L15)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `agentId` | string | 未声明非空 | 智能体标识 |
| `agentName` | string | 未声明非空 | 智能体名称 |
| `agentType` | string | 未声明非空 | 策略类型（step / loop / react） |
| `agentDesc` | string | 未声明非空 | 智能体职责描述 |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |
| `modelId` | string | 未声明非空 | 模型配置标识；与 clientId 不同 |
| `modelName` | string | 未声明非空 | 模型名称 |
| `modelType` | string | 未声明非空 | 模型类型 |
| `apiId` | string | 未声明非空 | API 配置标识 |
| `apiBaseUrl` | string | 未声明非空 | 模型服务基础地址 |
| `apiCompletionUrl` | string | 未声明非空 | 对话地址字段（保持后端原名） |
| `mcpInfoList` | array&lt;[workspace.AgentVO.McpInfo](#workspace-agentvo-mcpinfo)&gt; | 未声明非空 | 对象或条目数组；结构见类型链接 |
| `clientInfoList` | array&lt;[workspace.AgentVO.ClientInfo](#workspace-agentvo-clientinfo)&gt; | 未声明非空 | 对象或条目数组；结构见类型链接 |

<a id="workspace-agentvo-mcpinfo"></a>

### workspace.AgentVO.McpInfo

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/vo/AgentVO.java#L38)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `mcpId` | string | 未声明非空 | MCP 标识 |
| `mcpName` | string | 未声明非空 | MCP 名称 |
| `mcpType` | string | 未声明非空 | sse / stdio |
| `mcpParam` | string | 未声明非空 | 配置的 JSON 字符串，格式见对接指南 |
| `mcpDesc` | string | 未声明非空 | MCP 描述 |
| `mcpSecret` | string | 未声明非空 | MCP 凭据字符串；返回不保证脱敏 |

<a id="workspace-agentvo-clientinfo"></a>

### workspace.AgentVO.ClientInfo

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/vo/AgentVO.java#L53)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `clientId` | string | 未声明非空 | 客户端标识 |
| `clientRole` | string | 未声明非空 | 执行角色标识 |
| `promptId` | string | 未声明非空 | 系统提示词标识 |
| `systemPrompt` | string | 未声明非空 | 系统提示词正文 |
| `flowId` | number（整数） | 未声明非空 | 流程数值 ID，来自 AgentVO.clientInfoList |
| `userPrompt` | string | 未声明非空 | 用户提示词模板 |

<a id="workspace-commentvo"></a>

### workspace.CommentVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/vo/CommentVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `commentId` | string | 未声明非空 | 评论标识 |
| `plazaId` | string | 未声明非空 | 广场条目标识 |
| `userId` | number（整数） | 未声明非空 | 用户数值 ID |
| `userName` | string | 未声明非空 | 用户名 |
| `commentContent` | string | 未声明非空 | 评论正文 |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |
| `mine` | boolean | 未声明非空 | 是否为当前用户评论 |

<a id="workspace-plazavo"></a>

### workspace.PlazaVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/vo/PlazaVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `plazaId` | string | 未声明非空 | 广场条目标识 |
| `templateId` | string | 未声明非空 | 模板标识 |
| `agentName` | string | 未声明非空 | 智能体名称 |
| `agentType` | string | 未声明非空 | 策略类型（step / loop / react） |
| `userName` | string | 未声明非空 | 用户名 |
| `plazaTitle` | string | 未声明非空 | 发布标题 |
| `plazaDesc` | string | 未声明非空 | 发布描述 |
| `likeCount` | number（整数） | 未声明非空 | 点赞数 |
| `favorCount` | number（整数） | 未声明非空 | 收藏数 |
| `commentCount` | number（整数） | 未声明非空 | 评论数 |
| `liked` | boolean | 未声明非空 | 当前用户是否已点赞 |
| `favored` | boolean | 未声明非空 | 当前用户是否已收藏 |
| `commented` | boolean | 未声明非空 | 当前用户是否已评论 |
| `forked` | boolean | 未声明非空 | 当前用户是否已 Fork |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |

<a id="workspace-repovo"></a>

### workspace.RepoVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/vo/RepoVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `repoId` | string | 未声明非空 | 仓库条目标识 |
| `repoType` | string | 未声明非空 | self / fork / favor |
| `agentId` | string | 未声明非空 | 智能体标识 |
| `templateId` | string | 未声明非空 | 模板标识 |
| `agentName` | string | 未声明非空 | 智能体名称 |
| `agentType` | string | 未声明非空 | 策略类型（step / loop / react） |
| `agentDesc` | string | 未声明非空 | 智能体职责描述 |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |

<a id="workspace-templatevo"></a>

### workspace.TemplateVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/vo/TemplateVO.java#L15)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `templateId` | string | 未声明非空 | 模板标识 |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |
| `userName` | string | 未声明非空 | 用户名 |
| `plazaTitle` | string | 未声明非空 | 发布标题 |
| `plazaDesc` | string | 未声明非空 | 发布描述 |
| `likeCount` | number（整数） | 未声明非空 | 点赞数 |
| `favorCount` | number（整数） | 未声明非空 | 收藏数 |
| `commentCount` | number（整数） | 未声明非空 | 评论数 |
| `agentName` | string | 未声明非空 | 智能体名称 |
| `agentType` | string | 未声明非空 | 策略类型（step / loop / react） |
| `agentDesc` | string | 未声明非空 | 智能体职责描述 |
| `apiUrl` | string | 未声明非空 | 模板展示的 API 地址 |
| `apiCompletionUrl` | string | 未声明非空 | 对话地址字段（保持后端原名） |
| `modelName` | string | 未声明非空 | 模型名称 |
| `modelType` | string | 未声明非空 | 模型类型 |
| `mcpInfoList` | array&lt;[workspace.TemplateVO.McpInfo](#workspace-templatevo-mcpinfo)&gt; | 未声明非空 | 对象或条目数组；结构见类型链接 |
| `clientInfoList` | array&lt;[workspace.TemplateVO.ClientInfo](#workspace-templatevo-clientinfo)&gt; | 未声明非空 | 对象或条目数组；结构见类型链接 |

<a id="workspace-templatevo-mcpinfo"></a>

### workspace.TemplateVO.McpInfo

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/vo/TemplateVO.java#L47)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `mcpName` | string | 未声明非空 | MCP 名称 |
| `mcpType` | string | 未声明非空 | sse / stdio |
| `mcpParam` | string | 未声明非空 | 配置的 JSON 字符串，格式见对接指南 |
| `mcpDesc` | string | 未声明非空 | MCP 描述 |
| `requiredSecrets` | array&lt;string&gt; | 未声明非空 | 模板要求补充的凭据名称数组 |

<a id="workspace-templatevo-clientinfo"></a>

### workspace.TemplateVO.ClientInfo

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/model/vo/TemplateVO.java#L60)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `clientRole` | string | 未声明非空 | 执行角色标识 |
| `systemPrompt` | string | 未声明非空 | 系统提示词正文 |
| `userPrompt` | string | 未声明非空 | 用户提示词模板 |

## 管理员

[admin.AdvisorManageDTO](#admin-advisormanagedto) · [admin.AdvisorPageDTO](#admin-advisorpagedto) · [admin.AgentListDTO](#admin-agentlistdto) · [admin.AgentManageDTO](#admin-agentmanagedto) · [admin.AgentPageDTO](#admin-agentpagedto) · [admin.ApiManageDTO](#admin-apimanagedto) · [admin.ApiPageDTO](#admin-apipagedto) · [admin.ClientManageDTO](#admin-clientmanagedto) · [admin.ClientPageDTO](#admin-clientpagedto) · [admin.ConfigListDTO](#admin-configlistdto) · [admin.ConfigManageDTO](#admin-configmanagedto) · [admin.FlowManageDTO](#admin-flowmanagedto) · [admin.McpManageDTO](#admin-mcpmanagedto) · [admin.McpPageDTO](#admin-mcppagedto) · [admin.ModelManageDTO](#admin-modelmanagedto) · [admin.ModelPageDTO](#admin-modelpagedto) · [admin.PlazaManageDTO](#admin-plazamanagedto) · [admin.PlazaPageDTO](#admin-plazapagedto) · [admin.PromptManageDTO](#admin-promptmanagedto) · [admin.PromptPageDTO](#admin-promptpagedto) · [admin.TaskManageDTO](#admin-taskmanagedto) · [admin.TaskPageDTO](#admin-taskpagedto) · [admin.TemplateManageDTO](#admin-templatemanagedto) · [admin.TemplatePageDTO](#admin-templatepagedto) · [admin.UserManageDTO](#admin-usermanagedto) · [admin.UserPageDTO](#admin-userpagedto) · [admin.AdvisorVO](#admin-advisorvo) · [admin.AgentVO](#admin-agentvo) · [admin.ApiVO](#admin-apivo) · [admin.ClientDetailVO](#admin-clientdetailvo) · [admin.ClientVO](#admin-clientvo) · [admin.ConfigVO](#admin-configvo) · [admin.DashboardVO](#admin-dashboardvo) · [admin.DashboardVO.GraphInfo](#admin-dashboardvo-graphinfo) · [admin.DashboardVO.CountInfo](#admin-dashboardvo-countinfo) · [admin.DashboardVO.ChartValue](#admin-dashboardvo-chartvalue) · [admin.DashboardVO.BarValue](#admin-dashboardvo-barvalue) · [admin.DashboardVO.PieValue](#admin-dashboardvo-pievalue) · [admin.FlowVO](#admin-flowvo) · [admin.McpVO](#admin-mcpvo) · [admin.ModelVO](#admin-modelvo) · [admin.PlazaVO](#admin-plazavo) · [admin.PromptVO](#admin-promptvo) · [admin.SessionVO](#admin-sessionvo) · [admin.TaskVO](#admin-taskvo) · [admin.TemplateVO](#admin-templatevo) · [admin.UserVO](#admin-uservo)

<a id="admin-advisormanagedto"></a>

### admin.AdvisorManageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/AdvisorManageDTO.java#L13)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `advisorId` | string | NotBlank | 顾问标识 |
| `advisorName` | string | NotBlank | 顾问名称 |
| `advisorType` | string | NotBlank | 顾问类型 |
| `advisorParam` | string | 无字段注解 | 顾问配置字符串 |

<a id="admin-advisorpagedto"></a>

### admin.AdvisorPageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/AdvisorPageDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 无字段注解 | 查询关键词 |
| `pageNum` | number（整数） | NotNull；Min(1) | 页码，从 1 开始 |
| `pageSize` | number（整数） | NotNull；Min(1)；Max(10) | 每页条数 |

<a id="admin-agentlistdto"></a>

### admin.AgentListDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/AgentListDTO.java#L12)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 无字段注解 | 查询关键词 |
| `agentType` | string | 无字段注解 | 策略类型（step / loop / react） |

<a id="admin-agentmanagedto"></a>

### admin.AgentManageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/AgentManageDTO.java#L14)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `agentId` | string | NotBlank | 智能体标识 |
| `agentName` | string | NotBlank | 智能体名称 |
| `agentType` | string | NotBlank | 策略类型（step / loop / react） |
| `agentDesc` | string | NotBlank | 智能体职责描述 |
| `modelId` | string | 无字段注解 | 模型配置标识；与 clientId 不同 |
| `templateId` | string | 无字段注解 | 模板标识 |
| `agentStatus` | number（整数） | NotNull | 0 禁用 / 1 启用 |

<a id="admin-agentpagedto"></a>

### admin.AgentPageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/AgentPageDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 无字段注解 | 查询关键词 |
| `agentType` | string | 无字段注解 | 策略类型（step / loop / react） |
| `pageNum` | number（整数） | NotNull；Min(1) | 页码，从 1 开始 |
| `pageSize` | number（整数） | NotNull；Min(1)；Max(10) | 每页条数 |

<a id="admin-apimanagedto"></a>

### admin.ApiManageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/ApiManageDTO.java#L13)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `apiId` | string | NotBlank | API 配置标识 |
| `apiBaseUrl` | string | NotBlank | 模型服务基础地址 |
| `apiKey` | string | NotBlank | API 密钥；展示和日志中应脱敏 |
| `apiCompletionsPath` | string | NotBlank | 对话路径（管理员字段，复数 Completions） |
| `apiEmbeddingsPath` | string | NotBlank | 嵌入路径 |

<a id="admin-apipagedto"></a>

### admin.ApiPageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/ApiPageDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 无字段注解 | 查询关键词 |
| `pageNum` | number（整数） | NotNull；Min(1) | 页码，从 1 开始 |
| `pageSize` | number（整数） | NotNull；Min(1)；Max(10) | 每页条数 |

<a id="admin-clientmanagedto"></a>

### admin.ClientManageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/ClientManageDTO.java#L14)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `clientId` | string | NotBlank | 客户端标识 |
| `clientType` | string | NotBlank | 客户端类型 |
| `clientRole` | string | NotBlank | 执行角色标识 |
| `modelId` | string | NotBlank | 模型配置标识；与 clientId 不同 |
| `modelName` | string | NotBlank | 模型名称 |
| `clientName` | string | NotBlank | 客户端名称 |
| `clientStatus` | number（整数） | NotNull | 0 禁用 / 1 启用 |

<a id="admin-clientpagedto"></a>

### admin.ClientPageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/ClientPageDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 无字段注解 | 查询关键词 |
| `modelId` | string | 无字段注解 | 模型配置标识；与 clientId 不同 |
| `clientType` | string | 无字段注解 | 客户端类型 |
| `clientRole` | string | 无字段注解 | 执行角色标识 |
| `pageNum` | number（整数） | NotNull；Min(1) | 页码，从 1 开始 |
| `pageSize` | number（整数） | NotNull；Min(1)；Max(10) | 每页条数 |

<a id="admin-configlistdto"></a>

### admin.ConfigListDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/ConfigListDTO.java#L12)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 无字段注解 | 查询关键词 |
| `configType` | string | 无字段注解 | prompt / mcp / advisor |

<a id="admin-configmanagedto"></a>

### admin.ConfigManageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/ConfigManageDTO.java#L14)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `originClientId` | string | 无字段注解 | 更新前定位字段；变更对应键时传原值，省略时按新值定位 |
| `originConfigType` | string | 无字段注解 | 更新前定位字段；变更对应键时传原值，省略时按新值定位 |
| `originConfigValue` | string | 无字段注解 | 更新前定位字段；变更对应键时传原值，省略时按新值定位 |
| `clientId` | string | NotBlank | 客户端标识 |
| `configType` | string | NotBlank | prompt / mcp / advisor |
| `configValue` | string | NotBlank | 关联配置标识 |
| `configStatus` | number（整数） | NotNull | 0 禁用 / 1 启用 |

<a id="admin-flowmanagedto"></a>

### admin.FlowManageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/FlowManageDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `originAgentId` | string | 无字段注解 | 更新前定位字段；变更对应键时传原值，省略时按新值定位 |
| `originClientId` | string | 无字段注解 | 更新前定位字段；变更对应键时传原值，省略时按新值定位 |
| `agentId` | string | NotBlank | 智能体标识 |
| `clientId` | string | NotBlank | 客户端标识 |
| `clientRole` | string | NotBlank | 执行角色标识 |
| `userPrompt` | string | NotBlank | 用户提示词模板 |
| `flowSeq` | number（整数） | NotNull；Min(1) | 执行顺序，从 1 开始 |

<a id="admin-mcpmanagedto"></a>

### admin.McpManageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/McpManageDTO.java#L14)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `mcpId` | string | NotBlank | MCP 标识 |
| `mcpName` | string | NotBlank | MCP 名称 |
| `mcpType` | string | NotBlank | sse / stdio |
| `mcpDesc` | string | NotBlank | MCP 描述 |
| `mcpParam` | string | NotBlank | 配置的 JSON 字符串，格式见对接指南 |
| `mcpSecret` | string | 无字段注解 | MCP 凭据字符串；返回不保证脱敏 |
| `mcpTimeout` | number（整数） | NotNull | MCP 请求超时配置 |

<a id="admin-mcppagedto"></a>

### admin.McpPageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/McpPageDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 无字段注解 | 查询关键词 |
| `pageNum` | number（整数） | NotNull；Min(1) | 页码，从 1 开始 |
| `pageSize` | number（整数） | NotNull；Min(1)；Max(10) | 每页条数 |

<a id="admin-modelmanagedto"></a>

### admin.ModelManageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/ModelManageDTO.java#L13)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `modelId` | string | NotBlank | 模型配置标识；与 clientId 不同 |
| `apiId` | string | NotBlank | API 配置标识 |
| `modelName` | string | NotBlank | 模型名称 |
| `modelType` | string | NotBlank | 模型类型 |

<a id="admin-modelpagedto"></a>

### admin.ModelPageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/ModelPageDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 无字段注解 | 查询关键词 |
| `apiId` | string | 无字段注解 | API 配置标识 |
| `pageNum` | number（整数） | NotNull；Min(1) | 页码，从 1 开始 |
| `pageSize` | number（整数） | NotNull；Min(1)；Max(10) | 每页条数 |

<a id="admin-plazamanagedto"></a>

### admin.PlazaManageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/PlazaManageDTO.java#L14)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `plazaId` | string | NotBlank | 广场条目标识 |
| `templateId` | string | NotBlank | 模板标识 |
| `userId` | number（整数） | NotNull | 用户数值 ID |
| `agentName` | string | NotBlank | 智能体名称 |
| `agentType` | string | NotBlank | 策略类型（step / loop / react） |
| `plazaTitle` | string | NotBlank | 发布标题 |
| `plazaDesc` | string | NotBlank | 发布描述 |
| `likeCount` | number（整数） | NotNull | 点赞数 |
| `favorCount` | number（整数） | NotNull | 收藏数 |
| `commentCount` | number（整数） | NotNull | 评论数 |

<a id="admin-plazapagedto"></a>

### admin.PlazaPageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/PlazaPageDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 无字段注解 | 查询关键词 |
| `sortBy` | string | 无字段注解 | like / favor / comment；其他值按创建时间排序 |
| `sortOrder` | string | 无字段注解 | asc 升序；其他值降序 |
| `pageNum` | number（整数） | NotNull；Min(1) | 页码，从 1 开始 |
| `pageSize` | number（整数） | NotNull；Min(1)；Max(10) | 每页条数 |

<a id="admin-promptmanagedto"></a>

### admin.PromptManageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/PromptManageDTO.java#L13)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `promptId` | string | NotBlank | 系统提示词标识 |
| `promptName` | string | NotBlank | 提示词名称 |
| `systenPrompt` | string | NotBlank | 后端写入字段的实际拼写（systen，不是 system） |

<a id="admin-promptpagedto"></a>

### admin.PromptPageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/PromptPageDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 无字段注解 | 查询关键词 |
| `pageNum` | number（整数） | NotNull；Min(1) | 页码，从 1 开始 |
| `pageSize` | number（整数） | NotNull；Min(1)；Max(10) | 每页条数 |

<a id="admin-taskmanagedto"></a>

### admin.TaskManageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/TaskManageDTO.java#L14)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `taskId` | string | NotBlank | 定时任务标识 |
| `agentId` | string | NotBlank | 智能体标识 |
| `taskCron` | string | NotBlank | Spring 六字段 Cron |
| `taskDesc` | string | NotBlank | 定时任务描述 |
| `taskParam` | string | NotBlank | JSON 字符串，含 maxRetry/maxRound/maxPace/userMessage |
| `taskStatus` | number（整数） | NotNull | 0 禁用 / 1 启用 |

<a id="admin-taskpagedto"></a>

### admin.TaskPageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/TaskPageDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 无字段注解 | 查询关键词 |
| `agentId` | string | 无字段注解 | 智能体标识 |
| `pageNum` | number（整数） | NotNull；Min(1) | 页码，从 1 开始 |
| `pageSize` | number（整数） | NotNull；Min(1)；Max(10) | 每页条数 |

<a id="admin-templatemanagedto"></a>

### admin.TemplateManageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/TemplateManageDTO.java#L14)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `templateId` | string | NotBlank | 模板标识 |
| `userId` | number（整数） | NotNull | 用户数值 ID |
| `agentName` | string | NotBlank | 智能体名称 |
| `agentType` | string | NotBlank | 策略类型（step / loop / react） |
| `agentDesc` | string | NotBlank | 智能体职责描述 |
| `apiBaseUrl` | string | NotBlank | 模型服务基础地址 |
| `apiCompletionUrl` | string | NotBlank | 对话地址字段（保持后端原名） |
| `modelName` | string | NotBlank | 模型名称 |
| `modelType` | string | NotBlank | 模型类型 |
| `snapshot` | string | NotBlank | 模板快照 JSON 字符串，原样保留 |

<a id="admin-templatepagedto"></a>

### admin.TemplatePageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/TemplatePageDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 无字段注解 | 查询关键词 |
| `pageNum` | number（整数） | NotNull；Min(1) | 页码，从 1 开始 |
| `pageSize` | number（整数） | NotNull；Min(1)；Max(10) | 每页条数 |

<a id="admin-usermanagedto"></a>

### admin.UserManageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/UserManageDTO.java#L14)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `originUserName` | string | 无字段注解 | 更新前定位字段；变更对应键时传原值，省略时按新值定位 |
| `userName` | string | NotBlank | 用户名 |
| `password` | string | NotBlank | 密码 |
| `userRole` | string | NotBlank | 用户角色（user / admin） |
| `userAvatar` | string | NotBlank | 头像地址 |
| `userStatus` | number（整数） | NotNull | 0 禁用 / 1 启用 |

<a id="admin-userpagedto"></a>

### admin.UserPageDTO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/dto/UserPageDTO.java#L15)

| 字段 | JSON 类型 | 必填/约束；默认值 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 无字段注解 | 查询关键词 |
| `userRole` | string | 无字段注解 | 用户角色（user / admin） |
| `pageNum` | number（整数） | NotNull；Min(1) | 页码，从 1 开始 |
| `pageSize` | number（整数） | NotNull；Min(1)；Max(10) | 每页条数 |

<a id="admin-advisorvo"></a>

### admin.AdvisorVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/AdvisorVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `advisorId` | string | 未声明非空 | 顾问标识 |
| `advisorName` | string | 未声明非空 | 顾问名称 |
| `advisorType` | string | 未声明非空 | 顾问类型 |
| `advisorParam` | string | 未声明非空 | 顾问配置字符串 |
| `updateTime` | string（ISO 日期时间） | 未声明非空 | 更新时间；LocalDateTime，无时区偏移 |

<a id="admin-agentvo"></a>

### admin.AgentVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/AgentVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `agentId` | string | 未声明非空 | 智能体标识 |
| `agentName` | string | 未声明非空 | 智能体名称 |
| `agentType` | string | 未声明非空 | 策略类型（step / loop / react） |
| `agentDesc` | string | 未声明非空 | 智能体职责描述 |
| `modelId` | string | 未声明非空 | 模型配置标识；与 clientId 不同 |
| `modelName` | string | 未声明非空 | 模型名称 |
| `templateId` | string | 未声明非空 | 模板标识 |
| `agentStatus` | number（整数） | 未声明非空 | 0 禁用 / 1 启用 |
| `agentFrom` | number（整数） | 未声明非空 | 来源用户的数值 ID |
| `userName` | string | 未声明非空 | 用户名 |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |
| `updateTime` | string（ISO 日期时间） | 未声明非空 | 更新时间；LocalDateTime，无时区偏移 |

<a id="admin-apivo"></a>

### admin.ApiVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/ApiVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `apiId` | string | 未声明非空 | API 配置标识 |
| `apiBaseUrl` | string | 未声明非空 | 模型服务基础地址 |
| `apiKey` | string | 未声明非空 | API 密钥；展示和日志中应脱敏 |
| `apiCompletionsPath` | string | 未声明非空 | 对话路径（管理员字段，复数 Completions） |
| `apiEmbeddingsPath` | string | 未声明非空 | 嵌入路径 |
| `apiFrom` | number（整数） | 未声明非空 | 来源用户的数值 ID |
| `userName` | string | 未声明非空 | 用户名 |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |
| `updateTime` | string（ISO 日期时间） | 未声明非空 | 更新时间；LocalDateTime，无时区偏移 |

<a id="admin-clientdetailvo"></a>

### admin.ClientDetailVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/ClientDetailVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `clientId` | string | 未声明非空 | 客户端标识 |
| `clientRole` | string | 未声明非空 | 执行角色标识 |
| `client` | [admin.ClientVO](#admin-clientvo) | 未声明非空 | 对象或条目数组；结构见类型链接 |
| `model` | [admin.ModelVO](#admin-modelvo) | 未声明非空 | 对象或条目数组；结构见类型链接 |
| `api` | [admin.ApiVO](#admin-apivo) | 未声明非空 | 对象或条目数组；结构见类型链接 |
| `mcpList` | array&lt;[admin.McpVO](#admin-mcpvo)&gt; | 未声明非空 | 对象或条目数组；结构见类型链接 |
| `advisorList` | array&lt;[admin.AdvisorVO](#admin-advisorvo)&gt; | 未声明非空 | 对象或条目数组；结构见类型链接 |
| `promptList` | array&lt;[admin.PromptVO](#admin-promptvo)&gt; | 未声明非空 | 对象或条目数组；结构见类型链接 |

<a id="admin-clientvo"></a>

### admin.ClientVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/ClientVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `clientId` | string | 未声明非空 | 客户端标识 |
| `clientName` | string | 未声明非空 | 客户端名称 |
| `modelId` | string | 未声明非空 | 模型配置标识；与 clientId 不同 |
| `modelName` | string | 未声明非空 | 模型名称 |
| `clientFrom` | number（整数） | 未声明非空 | 来源用户的数值 ID |
| `userName` | string | 未声明非空 | 用户名 |
| `clientStatus` | number（整数） | 未声明非空 | 0 禁用 / 1 启用 |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |
| `updateTime` | string（ISO 日期时间） | 未声明非空 | 更新时间；LocalDateTime，无时区偏移 |

<a id="admin-configvo"></a>

### admin.ConfigVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/ConfigVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `clientId` | string | 未声明非空 | 客户端标识 |
| `configType` | string | 未声明非空 | prompt / mcp / advisor |
| `configValue` | string | 未声明非空 | 关联配置标识 |
| `configStatus` | number（整数） | 未声明非空 | 0 禁用 / 1 启用 |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |
| `updateTime` | string（ISO 日期时间） | 未声明非空 | 更新时间；LocalDateTime，无时区偏移 |

<a id="admin-dashboardvo"></a>

### admin.DashboardVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/DashboardVO.java#L15)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `countInfo` | [admin.DashboardVO.CountInfo](#admin-dashboardvo-countinfo) | 未声明非空 | 仪表盘计数对象 |
| `graphInfo` | [admin.DashboardVO.GraphInfo](#admin-dashboardvo-graphinfo) | 未声明非空 | 仪表盘图表对象 |

<a id="admin-dashboardvo-graphinfo"></a>

### admin.DashboardVO.GraphInfo

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/DashboardVO.java#L25)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `messageLastWeek` | array&lt;[admin.DashboardVO.ChartValue](#admin-dashboardvo-chartvalue)&gt; | 未声明非空 | 最近 7 天消息数量 |
| `messageLastMonth` | array&lt;[admin.DashboardVO.ChartValue](#admin-dashboardvo-chartvalue)&gt; | 未声明非空 | 最近 30 天消息数量 |
| `workUsage` | object；Map&lt;String, List&lt;[admin.DashboardVO.BarValue](#admin-dashboardvo-barvalue)&gt;&gt; | 未声明非空 | 按配置维度分组的 work 用量 |
| `chatUsage` | object；Map&lt;String, List&lt;[admin.DashboardVO.BarValue](#admin-dashboardvo-barvalue)&gt;&gt; | 未声明非空 | 按配置维度分组的 chat 用量 |
| `sessionWorkVsChat` | [admin.DashboardVO.PieValue](#admin-dashboardvo-pievalue) | 未声明非空 | 工作/聊天会话计数 |

<a id="admin-dashboardvo-countinfo"></a>

### admin.DashboardVO.CountInfo

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/DashboardVO.java#L46)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `apiCount` | number（整数） | 未声明非空 | api 记录数 |
| `modelCount` | number（整数） | 未声明非空 | model 记录数 |
| `clientCount` | number（整数） | 未声明非空 | client 记录数 |
| `agentCount` | number（整数） | 未声明非空 | agent 记录数 |
| `promptCount` | number（整数） | 未声明非空 | prompt 记录数 |
| `advisorCount` | number（整数） | 未声明非空 | advisor 记录数 |
| `mcpCount` | number（整数） | 未声明非空 | mcp 记录数 |
| `configCount` | number（整数） | 未声明非空 | config 记录数 |
| `flowCount` | number（整数） | 未声明非空 | flow 记录数 |
| `userCount` | number（整数） | 未声明非空 | user 记录数 |
| `sessionCount` | number（整数） | 未声明非空 | session 记录数 |
| `messageCount` | number（整数） | 未声明非空 | message 记录数 |
| `taskCount` | number（整数） | 未声明非空 | task 记录数 |
| `plazaCount` | number（整数） | 未声明非空 | plaza 记录数 |

<a id="admin-dashboardvo-chartvalue"></a>

### admin.DashboardVO.ChartValue

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/DashboardVO.java#L67)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `date` | string | 未声明非空 | 日期字符串 |
| `count` | number（整数） | 未声明非空 | 计数 |

<a id="admin-dashboardvo-barvalue"></a>

### admin.DashboardVO.BarValue

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/DashboardVO.java#L76)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 未声明非空 | 维度标识 |
| `value` | number（整数） | 未声明非空 | 数值 |

<a id="admin-dashboardvo-pievalue"></a>

### admin.DashboardVO.PieValue

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/DashboardVO.java#L85)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `workCount` | number（整数） | 未声明非空 | 工作会话数量 |
| `chatCount` | number（整数） | 未声明非空 | 聊天会话数量 |

<a id="admin-flowvo"></a>

### admin.FlowVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/FlowVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `agentId` | string | 未声明非空 | 智能体标识 |
| `clientId` | string | 未声明非空 | 客户端标识 |
| `clientRole` | string | 未声明非空 | 执行角色标识 |
| `userPrompt` | string | 未声明非空 | 用户提示词模板 |
| `flowSeq` | number（整数） | 未声明非空 | 执行顺序，从 1 开始 |
| `updateTime` | string（ISO 日期时间） | 未声明非空 | 更新时间；LocalDateTime，无时区偏移 |

<a id="admin-mcpvo"></a>

### admin.McpVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/McpVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `mcpId` | string | 未声明非空 | MCP 标识 |
| `mcpName` | string | 未声明非空 | MCP 名称 |
| `mcpType` | string | 未声明非空 | sse / stdio |
| `mcpParam` | string | 未声明非空 | 配置的 JSON 字符串，格式见对接指南 |
| `mcpSecret` | string | 未声明非空 | MCP 凭据字符串；返回不保证脱敏 |
| `mcpDesc` | string | 未声明非空 | MCP 描述 |
| `mcpTimeout` | number（整数） | 未声明非空 | MCP 请求超时配置 |
| `mcpFrom` | number（整数） | 未声明非空 | 来源用户的数值 ID |
| `userName` | string | 未声明非空 | 用户名 |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |
| `updateTime` | string（ISO 日期时间） | 未声明非空 | 更新时间；LocalDateTime，无时区偏移 |

<a id="admin-modelvo"></a>

### admin.ModelVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/ModelVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `modelId` | string | 未声明非空 | 模型配置标识；与 clientId 不同 |
| `apiId` | string | 未声明非空 | API 配置标识 |
| `modelName` | string | 未声明非空 | 模型名称 |
| `modelType` | string | 未声明非空 | 模型类型 |
| `modelFrom` | number（整数） | 未声明非空 | 来源用户的数值 ID |
| `userName` | string | 未声明非空 | 用户名 |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |
| `updateTime` | string（ISO 日期时间） | 未声明非空 | 更新时间；LocalDateTime，无时区偏移 |

<a id="admin-plazavo"></a>

### admin.PlazaVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/PlazaVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `plazaId` | string | 未声明非空 | 广场条目标识 |
| `templateId` | string | 未声明非空 | 模板标识 |
| `userId` | number（整数） | 未声明非空 | 用户数值 ID |
| `userName` | string | 未声明非空 | 用户名 |
| `agentName` | string | 未声明非空 | 智能体名称 |
| `agentType` | string | 未声明非空 | 策略类型（step / loop / react） |
| `plazaTitle` | string | 未声明非空 | 发布标题 |
| `plazaDesc` | string | 未声明非空 | 发布描述 |
| `likeCount` | number（整数） | 未声明非空 | 点赞数 |
| `favorCount` | number（整数） | 未声明非空 | 收藏数 |
| `commentCount` | number（整数） | 未声明非空 | 评论数 |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |
| `updateTime` | string（ISO 日期时间） | 未声明非空 | 更新时间；LocalDateTime，无时区偏移 |

<a id="admin-promptvo"></a>

### admin.PromptVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/PromptVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `promptId` | string | 未声明非空 | 系统提示词标识 |
| `promptName` | string | 未声明非空 | 提示词名称 |
| `systemPrompt` | string | 未声明非空 | 系统提示词正文 |
| `updateTime` | string（ISO 日期时间） | 未声明非空 | 更新时间；LocalDateTime，无时区偏移 |

<a id="admin-sessionvo"></a>

### admin.SessionVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/SessionVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `sessionId` | string | 未声明非空 | 会话标识 |
| `userName` | string | 未声明非空 | 用户名 |
| `sessionTitle` | string | 未声明非空 | 会话标题 |
| `sessionType` | string | 未声明非空 | chat / work |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |

<a id="admin-taskvo"></a>

### admin.TaskVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/TaskVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `taskId` | string | 未声明非空 | 定时任务标识 |
| `agentId` | string | 未声明非空 | 智能体标识 |
| `taskCron` | string | 未声明非空 | Spring 六字段 Cron |
| `taskDesc` | string | 未声明非空 | 定时任务描述 |
| `taskParam` | string | 未声明非空 | JSON 字符串，含 maxRetry/maxRound/maxPace/userMessage |
| `taskStatus` | number（整数） | 未声明非空 | 0 禁用 / 1 启用 |
| `taskFrom` | number（整数） | 未声明非空 | 来源用户的数值 ID |
| `userName` | string | 未声明非空 | 用户名 |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |
| `updateTime` | string（ISO 日期时间） | 未声明非空 | 更新时间；LocalDateTime，无时区偏移 |

<a id="admin-templatevo"></a>

### admin.TemplateVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/TemplateVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `templateId` | string | 未声明非空 | 模板标识 |
| `userId` | number（整数） | 未声明非空 | 用户数值 ID |
| `userName` | string | 未声明非空 | 用户名 |
| `agentName` | string | 未声明非空 | 智能体名称 |
| `agentType` | string | 未声明非空 | 策略类型（step / loop / react） |
| `agentDesc` | string | 未声明非空 | 智能体职责描述 |
| `apiBaseUrl` | string | 未声明非空 | 模型服务基础地址 |
| `apiCompletionUrl` | string | 未声明非空 | 对话地址字段（保持后端原名） |
| `modelName` | string | 未声明非空 | 模型名称 |
| `modelType` | string | 未声明非空 | 模型类型 |
| `snapshot` | string | 未声明非空 | 模板快照 JSON 字符串，原样保留 |
| `createTime` | string（ISO 日期时间） | 未声明非空 | 创建时间；LocalDateTime，无时区偏移 |
| `updateTime` | string（ISO 日期时间） | 未声明非空 | 更新时间；LocalDateTime，无时区偏移 |

<a id="admin-uservo"></a>

### admin.UserVO

[源码](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/model/vo/UserVO.java#L14)

| 字段 | JSON 类型 | 空值契约 | 说明 |
| --- | --- | --- | --- |
| `userId` | number（整数） | 未声明非空 | 用户数值 ID |
| `userName` | string | 未声明非空 | 用户名 |
| `userRole` | string | 未声明非空 | 用户角色（user / admin） |
| `userAvatar` | string | 未声明非空 | 头像地址 |
| `userStatus` | number（整数） | 未声明非空 | 0 禁用 / 1 启用 |
| `updateTime` | string（ISO 日期时间） | 未声明非空 | 更新时间；LocalDateTime，无时区偏移 |

