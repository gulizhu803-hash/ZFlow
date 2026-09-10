# ZFlow 前端接口对接文档

本文件依据仓库当前后端源码整理，核对日期：2026-09-10。覆盖 5 个业务 Controller、125 个接口，以及 90 个请求/响应结构（含嵌套类型）。本文描述实际实现；示例 ID、账号与密钥均为占位值。接口库存经过静态核对；后续完成的真实联调范围、修复与证据见 [真实联调记录](LIVE-INTEGRATION.md)，不代表 125 个接口全部实测通过。

- [完整接口清单](API-ENDPOINTS.md)：每条路由、参数位置、返回类型、用途和 Controller 源码链接。
- [字段字典](API-SCHEMAS.md)：请求必填项、校验注解、默认值和响应的完整嵌套结构。
- 本文导航：[公共协议](#protocol) · [登录与资料](#auth) · [聊天与会话](#chat) · [任务 SSE](#work) · [知识库](#rag) · [个人设置](#settings) · [工作区与广场](#workspace) · [管理员](#admin) · [现有前端差异](#frontend) · [核验依据](#sources)。

<a id="protocol"></a>

## 1. 公共协议

### 1.1 地址和请求方式

当前开发配置：

| 项目 | 值 |
| --- | --- |
| 后端端口 | `8066` |
| Servlet Context Path | `/miniagent/api/v1` |
| 完整 API 根地址 | `http://localhost:8066/miniagent/api/v1` |
| 业务接口方法 | 全部为 **POST**，包括列表、详情和删除 |
| 普通 JSON 请求 | `Content-Type: application/json` |
| 文件/资料上传 | `multipart/form-data`，由浏览器生成 boundary |
| 流请求 | `Accept: text/event-stream` |

示例：`POST http://localhost:8066/miniagent/api/v1/user/auth/login`。

新版 `app/` 前端默认使用同源 `/miniagent/api/v1`。当前 Docker 后端未向宿主映射 8066，开发服务器 5180 通过 Vite 代理访问 Docker Nginx 的 18080；直接在宿主运行 Java 时才把代理目标切换为 8066。参见 `.env.example`。

现有 [request.js](../src/request/request.js) 的 `BASE_URL` 是 `VITE_API_BASE || 'http://localhost:8066/miniagent'`；[api.js](../src/request/api.js) 的路径已经带 `/api/v1`。沿用这套封装时，`VITE_API_BASE` 应截止到 `/miniagent`；本文独立 fetch 示例的 `API_ROOT` 则包含 `/api/v1`，两种拼接方式不要混用。

部署时 [nginx.conf](../nginx.conf) 转发 `/miniagent/api/`，路径不重写；流响应关闭代理缓冲与缓存，读取超时配置为 300 秒。开发环境默认 CORS origin pattern 包含 `http://localhost:*`、`http://127.0.0.1:*`，允许请求头和携带凭据；实际环境以部署配置为准。

### 1.2 JWT 和权限

`Authorization: Bearer <token>`

- 登录、注册无需 JWT；业务白名单还包含 Actuator health 路径。OPTIONS 预检放行。
- 其他业务接口均需 JWT；`/admin/*` 还要求 `userRole=admin`。
- 未登录/无效 JWT：HTTP **401**。无管理员权限：HTTP **403**。
- 上述拦截器错误的 JSON 业务码仍是 `500`，不要通过 `body.code === 401` 判断失效。
- 没有独立 refresh-token、logout 接口。查询/编辑个人资料会返回新 token，前端应更新保存的认证数据；退出登录清除前端认证状态即可。
- token 有效期由 `miniagent.jwt.expire-seconds` 配置和 JWT 的 `exp` 决定，前端不要写死过期时长。

### 1.3 普通响应和错误

普通接口返回 `Result<T>`：

~~~json
{
  "code": 200,
  "info": "成功",
  "data": null
}
~~~

`data` 是接口对应的业务对象、列表或分页对象；`Result<Void>` 成功时为 `null`。业务失败通常如下：

~~~json
{
  "code": 500,
  "info": "具体错误提示或统一异常提示",
  "data": null
}
~~~

**多数校验/业务异常是 HTTP 200 + code 500。** 全局异常处理器没有统一把业务异常映射成 HTTP 4xx/5xx；鉴权错误则有实际 HTTP 401/403。`WorkException` 通常被转成通用提示“系统异常，请检查输入是否正确或联系管理员处理”；不能假设所有错误都暴露原始业务原因。断连、代理错误等也不保证返回 JSON。

前端需要同时检查 HTTP 状态和业务码。以下辅助函数只用于 `Result<T>` 接口，返回值已经解包为 `data`：

~~~js
const API_ROOT = 'http://localhost:8066/miniagent/api/v1';

async function postResult(path, { token, query, json, form, signal } = {}) {
  const url = new URL(API_ROOT + path);
  for (const [key, value] of Object.entries(query || {})) {
    if (value !== undefined && value !== null) url.searchParams.set(key, String(value));
  }

  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  if (json !== undefined) headers['Content-Type'] = 'application/json';

  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: form ?? (json === undefined ? undefined : JSON.stringify(json)),
    signal
  });
  const text = await response.text();
  let result;
  try { result = JSON.parse(text); } catch { /* 下方处理非 JSON 错误 */ }

  if (response.status === 401) throw new Error('登录已失效，请重新登录');
  if (response.status === 403) throw new Error('无访问权限');
  if (!response.ok) throw new Error(result?.info || 'HTTP ' + response.status);
  if (!result || result.code !== 200) {
    throw new Error(result?.info || '响应不是成功的 Result');
  }
  return result.data;
}
~~~

不要同时传 `json` 和 `form`。应用层捕获 401 后清除认证并跳转登录；业务错误展示 `info`。本函数不适用于 `/ai/chat/complete` 和两个流接口。

### 1.4 分页、类型与更新规则

所有分页结果使用以下字段，**不是 records/pages**：

~~~json
{
  "code": 200,
  "info": "成功",
  "data": {
    "total": 12,
    "pageNum": 1,
    "pageSum": 2,
    "pageSize": 10,
    "list": []
  }
}
~~~

上例只展示分页外形，`list` 的元素见对应 VO。页码从 1 开始；管理端分页与广场分页的 `pageSize` 为 1..10，评论分页为 1..20。即使默认第一页，分页 DTO 也要求显式传 `pageNum/pageSize`。

`LocalDateTime` 按 ISO 日期时间字符串返回，如 `2026-09-10T10:30:00`，没有确定的时区偏移。Java `Long` 默认是 JSON 数字；数值 ID 超过 `Number.MAX_SAFE_INTEGER` 时应无损解析，简单转成字符串不能恢复已经丢失的精度。

多数更新接口复用新增 DTO；必填字段必须完整提交，不能当作 PATCH 仅发变化字段。字段字典区分“注解必填”和“业务上需要”，例如个人模型更新必须带 `apiId`，虽然该字段没有 `@NotBlank`。

<a id="auth"></a>

## 2. 登录、注册与个人资料

路由及字段见[用户接口](API-ENDPOINTS.md#user)、[AuthDTO](API-SCHEMAS.md#user-authdto)。

登录和注册请求相同，后端字段是 `userName`：

~~~json
{
  "userName": "demo-user",
  "password": "<password>"
}
~~~

登录响应示例：

~~~json
{
  "code": 200,
  "info": "成功",
  "data": {
    "token": "<jwt>",
    "userName": "demo-user",
    "userRole": "user",
    "userAvatar": "/avatar/example.png",
    "userStatus": 1
  }
}
~~~

`AuthVO` 是平铺结构，没有 `data.user`，也不返回密码。用户名/密码不可空白；重复注册和禁用用户登录会失败。前端可以在适配层构造自己的 user 对象，不应把适配后的结构当成后端协议。

`POST /user/profile/query` 不需要请求体。编辑资料使用 `profile` JSON part，头像使用可选的 `avatar` file part：

~~~js
const form = new FormData();
form.append('profile', new Blob([JSON.stringify({
  userName: 'demo-user',
  oldPassword: '<old-password>',
  newPassword: '<new-password>'
})], { type: 'application/json' }));

// 不改头像时不追加 avatar。
// form.append('avatar', avatarFile);
const auth = await postResult('/user/profile/edit', { token, form });
// 用 auth 更新本地 token 和用户资料。
~~~

`userName` 必传；`oldPassword/newPassword` 要么同时提供，要么同时省略。只改名称/头像时省略两个密码字段。不要把 `profile` 作为普通 JSON 文本字段提交，也不要手写整个 multipart 的 Content-Type。

<a id="chat"></a>

## 3. 聊天与会话

### 3.1 推荐调用顺序

1. 登录后调用 `/user/query/chat-client`，取得可选的 `clientId`；可同时查询 `/user/query/mcp` 和 `/user/query/rag`。
2. 选择客户端后调用 `/ai/armory`，传 `{"armoryType":"chat","armoryId":"<clientId>"}`，完成装配。
3. 查询 `/session/list`，选择 chat 会话；或调用 `/session/insert` 创建。
4. 加载 `/session/message/chat?sessionId=...`，再发送完整/流式聊天请求。
5. 切换会话时按 sessionId 重新加载历史，客户端模型 ID 与会话 ID 分别维护。

创建会话是 **POST + Query**：

~~~js
const session = await postResult('/session/insert', {
  token,
  query: { sessionTitle: '新对话', sessionType: 'chat' }
});
// session.sessionId 用于后续消息请求。
~~~

`sessionType` 使用 `chat/work`；创建入口没有专门的枚举校验，前端仍应只发送这两个受支持的值。每位用户每种 sessionType 最多 3 个会话。改名和删除要求会话属于本人；管理员可读取其他用户消息。更新时空白标题会变成“未命名会话”。

### 3.2 聊天请求体

`/ai/chat/complete` 与 `/ai/chat/stream` 共用 [AiChatDTO](API-SCHEMAS.md#ai-aichatdto)：

~~~json
{
  "clientId": "<clientId>",
  "sessionId": "<chatSessionId>",
  "userMessage": "请总结这份资料",
  "mcpIdList": [],
  "ragTag": "<ragTag>",
  "temperature": 0.6,
  "presencePenalty": 0.0,
  "maxCompletionTokens": 8192
}
~~~

必填 `clientId/sessionId/userMessage`。不使用知识库时省略 `ragTag`；其他可选项也可省略。三个数值选项的默认值如上，DTO 没有声明数值范围约束；上游模型仍可能拒绝不支持的值。使用默认值时应省略字段，不发送 `null`。

**`clientId` 来自 `/user/query/chat-client`；`/user/query/model` 的 `modelId` 供工作区创建/编辑模型使用，两者不能替换。**

### 3.3 完整聊天是原始文本

`POST /ai/chat/complete` 返回 `String`，用 `response.text()` 读取，不要读取 `result.data`。模型不可用/调用失败也可能返回 HTTP 200 和普通错误文本“模型异常 / 模型不可用，请报告管理员”；会话校验失败也可能直接返回原因文本。

DTO 校验或鉴权在进入方法前失败时，仍可能返回全局 JSON 错误。因此先处理 HTTP 状态与 Content-Type，再区分 JSON 错误和原始文本。现有协议没有结构化的“文本成功/文本错误”状态字段。

### 3.4 流式聊天是文本片段

`POST /ai/chat/stream` 返回 `Flux<String>`，Controller 没有显式固定 produces。前端须发送 `Accept: text/event-stream` 协商 SSE，并检查实际响应类型。

协商成 SSE 后，数据类似：

~~~text
data:你好

data:，有什么可以帮你？

~~~

- `data` 是原始文本片段，不是 OpenAI 的 `choices[0].delta.content`。
- 追加文本时保留空格和换行，不要 `trim()`，也不要对聊天数据无条件 `JSON.parse`。
- 后端没有明确发送 `[DONE]` 或命名 complete 事件；正常流关闭表示结束。
- 模型错误可作为普通文本片段发送；不能仅靠 SSE 事件名识别聊天错误。
- 流关闭/取消时，已积累的助手部分文本可能被持久化。

### 3.5 历史消息与数量限制

[MessageVO](API-SCHEMAS.md#session-messagevo) 字段为 `messageContent/messageRole/messageType/messageSeq/createTime`，没有 messageId。`messageSeq` 只在同一会话、同一消息类型内有意义，前端列表键可组合 sessionId、messageType、messageSeq。

历史分三条路由：

| 路由 | 内容 |
| --- | --- |
| `/session/message/chat` | chat 用户输入与助手答复 |
| `/session/message/work-sse` | 任务阶段事件；messageContent 是序列化的 ExecuteResponseEntity |
| `/session/message/work-answer` | 任务用户输入与最终总结答复 |

源码中的用户消息上限为 chat 20 条、work 3 条，计数只包含对应类型的 user 消息。但限制发生在持久化阶段，Controller 捕获并记录持久化异常，**不是可靠的执行前拒绝或限流契约**。超限时可能执行成功但用户消息未存储。前端可根据已知历史限制输入，并提示创建/切换会话，不能依赖后端一定返回“已达上限”。

<a id="work"></a>

## 4. 智能体执行与任务 SSE

### 4.1 请求

查询 `/user/query/work-agent` 后，先装配：

~~~json
{
  "armoryType": "work",
  "armoryId": "<agentId>"
}
~~~

创建/选择 `sessionType=work` 的会话，再向 `POST /ai/work/execute` 发送：

~~~json
{
  "agentId": "<agentId>",
  "agentDesc": "<所选智能体返回的 agentDesc>",
  "sessionId": "<workSessionId>",
  "userMessage": "请完成本次任务",
  "maxRound": 3,
  "maxRetry": 2,
  "maxPace": 5
}
~~~

[AiWorkDTO](API-SCHEMAS.md#ai-aiworkdto) 所有字段都必传。三个整数都必须 ≥1，即使当前策略只使用其中一项。策略由所选智能体决定，执行请求没有 strategy 字段。`agentDesc` 用于任务匹配检查，应使用查询结果中的描述。

智能体未启用/不存在、会话不可访问或类型不符、任务与智能体定位不匹配，均可能以 `event:error` 返回并关闭连接。DTO 校验/鉴权失败可能在建立 SSE 前返回 JSON 错误。

### 4.2 事件结构

使用 `fetch` 发 POST 并携带 Authorization，原生 `EventSource` 无法直接满足这里的请求方式与自定义请求头。

| SSE event | data | 前端处理 |
| --- | --- | --- |
| `message` | ExecuteResponseEntity JSON | 解析对象，按角色/阶段与轮次展示 |
| `complete` | ExecuteResponseEntity JSON，clientType 为 complete | 标记任务执行流完成 |
| `error` | 原始错误字符串 | 展示失败，不能假设 data 是 JSON |

message 示例：

~~~text
event:message
id:1789000000000
data:{"clientType":"PLANNER","sectionType":"planner_step","sectionContent":"[{\"step\":1,\"task\":\"收集资料\"}]","round":null,"pace":null,"step":null,"timestamp":1789000000000,"sessionId":"s-example"}

~~~

`sectionContent` 是字符串，可能包含 JSON 文本或自然语言；上例的内部内容只示意显示方式，不是固定的 Planner 子结构契约。第一次 JSON.parse 得到事件对象，只有对应阶段确实需要结构化呈现时才尝试解析 sectionContent，并保留纯文本兜底。

完成示例：

~~~text
event:complete
data:{"clientType":"complete","sectionType":null,"sectionContent":"执行完成","round":null,"pace":null,"step":null,"timestamp":1789000001000,"sessionId":"s-example"}

~~~

错误示例：

~~~text
event:error
data:AI 未启用或不存在

~~~

服务端策略发送完成事件后，由调度层关闭 emitter。完整流结束不等于任务目标一定达成，应结合最终总结和阶段状态展示结果；如果未见 complete 就断流，应保留“连接中断/执行状态未知”状态。每个阶段不会单独发送 complete。

### 4.3 SSE 客户端实现要求

1. 先判断 HTTP 401/403 与其他 HTTP 错误；如果响应是 JSON，按 Result 错误处理，不能盲目交给 SSE 解析器。
2. 持续使用 `TextDecoder.decode(value, {stream:true})`，结束时 flush；字符和事件都可能跨网络 chunk。
3. 按空行分隔事件，兼容 LF/CRLF（解析通用 SSE 时也应支持 CR）。一个网络 chunk 可能含多个事件。
4. 保留 `event/id/data`；多行 data 用 `\n` 拼接，忽略注释行。移除 `data:` 后只剥离协议允许的一个前导空格，保留内容空白。
5. 聊天 data 作为字符串追加；任务 message/complete 才按实体 JSON 解析；任务 error 始终保留原始文本。
6. 维护“收到 complete”“收到 error”“网络 EOF/异常”三种状态，避免 error 后因 onDone 被覆盖成成功。
7. `AbortController` 可关闭前端连接。当前没有任务取消接口，断开连接不保证停止后台线程或撤销已执行的工具操作。
8. `id` 来自毫秒时间戳；不是可靠的全局唯一操作 ID。没有 Last-Event-ID 续传/重放实现，不要自动重试执行 POST，以免重复任务。

服务端使用 `SseEmitter(0L)`，不等于整条链路永不超时：代理、浏览器和模型调用仍有自己的超时。

### 4.4 实际 sectionType 枚举

以下 29 个值来自 `AiSectionType`，不要使用预览页面的示例阶段名代替。

| 策略 | sectionType | 含义 |
| --- | --- | --- |
| loop | `analyzer_demand` | 任务需求分析 |
| loop | `analyzer_history` | 执行历史评估 |
| loop | `analyzer_strategy` | 执行策略制定 |
| loop | `analyzer_progress` | 完成度评估 |
| loop | `analyzer_status` | 任务状态 |
| loop | `performer_target` | 执行目标 |
| loop | `performer_process` | 执行过程 |
| loop | `performer_result` | 执行结果 |
| loop | `supervisor_issue` | 问题识别 |
| loop | `supervisor_suggestion` | 改进建议 |
| loop | `supervisor_score` | 质量评分 |
| loop | `supervisor_status` | 监督状态 |
| loop | `summarizer_overview` | 任务总结 |
| step | `inspector_mcp` | MCP 工具 |
| step | `planner_step` | 任务步骤 |
| step | `runner_result` | 运行结果 |
| step | `runner_status` | 运行状态 |
| step | `replier_overview` | 任务回复 |
| react | `observer_demand` | 任务目标解析 |
| react | `observer_history` | 现状观察 |
| react | `observer_judgement` | 继续判断 |
| react | `observer_status` | 观察状态 |
| react | `reasoner_target` | 下一步目标 |
| react | `reasoner_action` | 下一步动作 |
| react | `reasoner_acceptance` | 验收条件 |
| react | `actor_target` | 执行目标 |
| react | `actor_process` | 执行过程 |
| react | `actor_result` | 执行结果 |
| react | `evaluator_overview` | 最终结论 |

角色名通常是对应前缀的大写，如 ANALYZER、PLANNER、ACTOR；完成实体的 clientType 特例为小写 `complete`。`round/pace/step` 根据策略使用，未使用的字段可为 null/缺省，前端不能假设全有值。

阶段输出存入 work-sse 历史；`summarizer_overview/replier_overview/evaluator_overview` 另存为 work-answer 的 assistant 消息。历史 JSON 使用的序列化方式可能省略 null 字段，重放时同样需要容错。

<a id="rag"></a>

## 5. 知识库导入

### 5.1 文件

`POST /ai/rag/file` 是 multipart，同名 `fileList` 重复追加：

~~~js
const form = new FormData();
form.append('ragTag', '产品资料');
for (const file of selectedFiles) form.append('fileList', file);

await postResult('/ai/rag/file', { token, form });
const tags = await postResult('/user/query/rag', { token });
~~~

文件字段是 `fileList`，不是 `file/files`。后端通过 Tika 读取文件；前端目前只选择 txt/md 是 UI 限制，不能写成后端只支持这两类。实际解析能力以 Tika/文件内容为准。

接口同步读取、切分与向量化，可能耗时。至少一个文件成功即可返回成功，单个失败只记录日志，没有逐文件状态。Nginx 的请求体上限配置为 20m；Spring multipart 和其他部署层可能有更低限制，不能承诺“单文件支持 20 MB”。

### 5.2 Git

`POST /ai/rag/git` 请求：

~~~json
{
  "repoUrl": "https://example.com/team/project.git",
  "username": "<git-username>",
  "password": "<git-access-token>"
}
~~~

该 DTO/入口没有 Bean Validation 注解，但成功克隆仍需要有效仓库地址和适用的访问凭据。字段是小写 `username`，与登录的 `userName` 不同。知识库标签由 URL 最后一段去掉 `.git` 得到；当前仅采集 `.java/.html/.md/.txt` 并跳过隐藏目录。

导入同步执行，成功为 `Result<Void>`。向量元数据包含知识标签与用户 ID，按用户隔离。现有接口没有导入进度、文档列表、单文档删除/下载或批次任务状态；UI 完成后刷新 `/user/query/rag`。

<a id="settings"></a>

## 6. 个人模型、MCP 与定时任务

完整字段见[用户设置 DTO](API-SCHEMAS.md#user-settingapimodeldto)，路由见[用户清单](API-ENDPOINTS.md#user)。

### 6.1 模型/API 配置

新增示例：

~~~json
{
  "modelName": "<provider-model-name>",
  "modelType": "chat",
  "apiBaseUrl": "https://api.example.com",
  "apiKey": "<api-key>",
  "apiCompletionPath": "/v1/chat/completions"
}
~~~

`modelType` 按实际模型配置使用，上例仅示意；DTO 只校验非空白。更新使用同一结构并加原 `apiId`，删除也是 Query `apiId`，不是 modelId。列表可选 Query `keyword`，默认空字符串。

用户设置使用单数 `apiCompletionPath`；管理员 API 配置使用复数 `apiCompletionsPath`。用户模型列表响应包含 apiKey，不保证后端脱敏。

### 6.2 MCP

`mcpType` 的实现值为 `sse/stdio`。新增/更新必填 `mcpName/mcpType/mcpDesc`；更新需原 mcpId。`mcpParam/mcpSecret` 在 DTO 中是字符串，不能直接发对象。

SSE 配置示例：

~~~json
{
  "mcpName": "示例工具",
  "mcpType": "sse",
  "mcpDesc": "工具描述",
  "mcpParam": "{\"baseUri\":\"http://mcp.example.com\",\"sseEndPoint\":\"/sse\"}",
  "mcpSecret": "<credential-string>"
}
~~~

注意 `sseEndPoint` 的大小写。stdio 的 mcpParam 内容为：

~~~json
{
  "command": "<executable>",
  "args": ["<argument>"],
  "env": {
    "EXAMPLE_TOKEN": "<token>"
  }
}
~~~

上面的 stdio 对象需要 `JSON.stringify` 后作为 mcpParam 发送。`mcpSecret` 是下游约定的凭据字符串，不统一保证是 JSON。SSE 装配会将其 Base64 编码放入配置的凭据请求头；stdio 使用 env。管理员的 `mcpTimeout` 按 `Duration.ofMinutes` 解释，单位为分钟。

配置通过写入接口的校验，不代表工具已连接成功；装配时才会解析并初始化 MCP。列表/编辑详情可能包含凭据，前端调试日志和展示需脱敏。

### 6.3 定时任务

新增请求：

~~~json
{
  "agentId": "<agentId>",
  "taskCron": "0 0 9 * * *",
  "taskDesc": "每日资料整理",
  "taskParam": "{\"maxRetry\":2,\"maxRound\":3,\"maxPace\":5,\"userMessage\":\"整理今日资料\"}",
  "taskStatus": 1
}
~~~

- Cron 由 Spring `CronTrigger` 解析，使用六字段“秒 分 时 日 月 周”。示例为服务器调度时区的每天 09:00，不保证等于浏览器时区。
- taskParam 是 JSON **字符串**；解析后的四个字段由调度器直接读取。即使写入 DTO 只校验非空白，前端也应确保三个整数为正数、userMessage 非空。
- taskStatus 默认 1；显式 null 不合法。状态按 0 禁用/1 启用使用，当前 DTO 未声明 0/1 范围注解。
- 更新加 `taskId`；删除 Query taskId；启停 Query taskId、taskStatus。
- 调度注册按后端的刷新定时器更新。保存成功只说明配置保存，不能当成任务已经运行或已成功注册。当前没有运行记录、下一次运行时间或手动触发任务的专用接口。

<a id="workspace"></a>

## 7. 工作区、仓库与广场

### 7.1 ID 和页面数据来源

| 页面行为 | 路由 / 数据 |
| --- | --- |
| 我的仓库 | `/workspace/repo/map` → self/fork/favor |
| 编辑已有智能体 | `/workspace/agent/detail?agentId=...` |
| 查看公开模板 | `/workspace/agent/template?templateId=...` |
| 广场列表 | `/workspace/plaza/page`，JSON 分页 |
| 广场评论 | `/workspace/plaza/comment-area`，JSON plazaId + 分页 |
| 创建智能体的模型选择 | `/user/query/model` |
| 创建智能体的角色说明 | `/user/query/role` |

repo/map 的 data 固定为：

~~~json
{
  "self": [],
  "fork": [],
  "favor": []
}
~~~

`agentId`、`templateId`、`plazaId`、`repoId` 语义不同。复制模板传 templateId；点赞/收藏/评论传 plazaId；编辑和删除本人智能体传 agentId。所有这些 Query 接口仍是 POST。

### 7.2 创建与编辑

创建请求：

~~~json
{
  "modelId": "<modelId>",
  "modelName": "<modelName>",
  "strategy": "step",
  "agentName": "资料助手",
  "agentDesc": "收集并整理指定资料",
  "mcpIdSet": []
}
~~~

前五个字段必传；strategy 支持 `step/loop/react`；mcpIdSet 可选，以 JSON 数组发送。创建过程调用系统 LLM 为策略角色生成提示词，需要后端模型配置可用，响应可能较慢。成功返回 Void，没有新 agentId；刷新 repo/map 获取新条目。

详情 [workspace.AgentVO](API-SCHEMAS.md#workspace-agentvo) 中，`mcpInfoList` 和 `clientInfoList` 是嵌套数组。提示词定位字段来自 clientInfoList：

~~~json
{
  "clientId": "<clientId>",
  "clientRole": "planner",
  "promptId": "<promptId>",
  "systemPrompt": "<system prompt>",
  "flowId": 123,
  "userPrompt": "<user prompt>"
}
~~~

五种独立更新：

| 路由后缀 | JSON 参数 |
| --- | --- |
| `/agent/update/base` | agentId、agentName、agentDesc |
| `/agent/update/model` | agentId、modelId、非空 clientIdList |
| `/agent/update/mcp` | agentId、非空 clientIdList、mcpIdList |
| `/agent/update/userprompt` | agentId、flowId、userPrompt |
| `/agent/update/systemprompt` | agentId、promptId、systemPrompt |

以上路径均带 `/workspace`。mcpIdList 必须提供数组：`[]` 会清空所选客户端的 MCP；虽然 DTO 未标必填，省略/null 会在业务循环中异常。clientIdList 使用详情中属于当前智能体的客户端 ID。更新提示词只传 clientRole 不够；必须带 flowId 或 promptId。

### 7.3 广场筛选与异步操作

广场查询请求：

~~~json
{
  "keyword": "",
  "sortBy": "like",
  "sortOrder": "desc",
  "pageNum": 1,
  "pageSize": 10
}
~~~

sortBy 支持 `like/favor/comment`；省略或其他值按创建时间排序。只有精确的 `asc` 为升序，其余按降序。列表提供 liked/favored/commented/forked 用户状态。模板详情与广场评论是两个接口，没有通用 plazaId → 全部详情的单一接口。

**以下操作通过 MQ 异步处理：**

- 点赞/取消点赞、收藏/取消收藏、发表评论/删除评论、删除广场条目。
- 发布智能体、Fork 模板、删除本人智能体。

这些接口返回 code 200 只表示发送任务的调用完成，不能保证数据库已变更或消费者最终成功。返回没有 operationId/status 查询地址。前端可显示“已提交”，采用乐观状态更新并进行有限次数刷新；不要因立即查询不到变化就重复发送。超时仍未一致时显示待确认并允许用户手动刷新。

发布请求是 agentId、plazaTitle、plazaDesc；Fork 使用 Query templateId。删除评论需要同时传 commentId、plazaId。其余参数和权限范围见[工作区清单](API-ENDPOINTS.md#workspace)。

<a id="admin"></a>

## 8. 管理端

[管理员接口清单](API-ENDPOINTS.md#admin) 覆盖全部 68 条路由，[管理员字段字典](API-SCHEMAS.md#admin-advisormanagedto) 覆盖请求与响应。均需 admin JWT。

| 模块 | 功能 |
| --- | --- |
| dashboard | countInfo 与 graphInfo 仪表盘 |
| api/model/mcp/advisor/prompt | 分页、新增、更新、删除 |
| client/agent/user/task | 上述 CRUD，加 toggle；agent 另有 list |
| config | 按 clientId 分组的列表、新增、更新、删除、toggle |
| flow | 可选客户端详情、智能体流程、新增、更新、删除 |
| session | 会话列表，消息通过 /session/message/* 读取 |
| template/plaza | 分页、新增、更新、删除 |
| list | clientType、agentType、configType、clientRole、userRole、apiId、modelId 字典 |

管理接口注意：

- 管理新增 DTO 通常要求显式提供业务 ID，不同于用户设置新增时由后端生成 ID。以字段字典为准。
- Prompt 新增/更新字段实际拼写为 **`systenPrompt`**，查询 VO 返回 **`systemPrompt`**。
- Config 列表的 Map 键是 clientId，configType 是筛选参数。配置以 clientId + configType + configValue 定位；更新键值时，传 originClientId/originConfigType/originConfigValue。
- Flow 以 agentId + clientId 定位；更换组合键时传 originAgentId/originClientId。
- 用户更新若改名需 originUserName，删除/启停以 **userName** 定位。UserManageDTO 在更新时仍要求 password、userAvatar 等非空。
- ConfigType 使用 prompt/mcp/advisor；角色和类型选择优先读取对应 list 字典，不硬编码展示名称。
- Admin ClientVO 本身没有 clientType/clientRole 两个字段；需要执行角色等详情时参考 ClientDetailVO 和流程接口，不能把 DTO 字段当成列表响应必有字段。
- 数据依赖冲突可能阻止删除；管理 Service 也会检查目标是否存在。失败按业务 code/info 处理，不要只检查 HTTP 状态。
- 仪表盘内部 CountInfo、GraphInfo、ChartValue、BarValue、PieValue 都是嵌套对象，字段字典已分别列出。

<a id="frontend"></a>

## 9. 现有前端适配差异与未提供能力

本节记录源码对照结果，**本次只写文档，没有修改请求封装或页面逻辑**。

| 位置/行为 | 对接结论 |
| --- | --- |
| request.js 的 base 与 api.js 路径 | API 版本前缀只能拼接一次 |
| 登录封装的用户标准化 | 后端实际 AuthVO 为平铺结构 |
| studioGenerate | 目前调用 unsupportedApi；没有对应后端接口 |
| api.js 中“以下接口后端当前未实现”注释 | 范围过时：workspace create/detail/update 已有后端实现 |
| studioDetail / plazaDetail | 都调用 agent/template，参数是 templateId |
| studioListMine | 从 repo/map 的 self 数组取数据 |
| studioUpdate 按 clientRole 分支 | clientRole 仅是封装分支条件；调用方仍必须提供 flowId/promptId |
| streamFetch/processEventChunk | 当前只用 LF 双换行分帧，未正确覆盖 CRLF 双换行；忽略 event/id，任务 error/complete 无法按事件名区分；对全部 data 尝试 JSON.parse 也可能改变聊天文本类型 |
| 流解析中的 data 前导空白 | 当前用 `\s*` 剥离，可能吞掉聊天空格；新适配应保留内容空白 |
| 预览事件 inspector_analysis / planner_plan | 示例数据，非真实后端 sectionType；真实值是 inspector_mcp / planner_step |

当前控制器没有：专用 studioGenerate、任意 DAG 保存、后台执行取消/续传、知识库文档 CRUD/导入进度、独立 refresh-token/logout、定时任务运行记录/手动触发接口。前端规划这些能力时需要新增后端协议，不能仅凭本地 mock 或包装函数名判定已支持。

<a id="sources"></a>

## 10. 核验依据与维护

接口路径、绑定方式和方法返回类型以 Controller 为准；字段以 DTO/VO 为准；业务行为另核对 Service、Repository、Mapper、拦截器和配置。

| 来源 | 核验内容 |
| --- | --- |
| [AiController](../../backend/zflow-trigger/src/main/java/com/zgl/zflow/trigger/controller/AiController.java) | AI 请求、原始文本/流、消息持久化行为 |
| [UserController](../../backend/zflow-trigger/src/main/java/com/zgl/zflow/trigger/controller/UserController.java) | 认证、multipart 资料、用户设置 |
| [SessionController](../../backend/zflow-trigger/src/main/java/com/zgl/zflow/trigger/controller/SessionController.java) | 会话与历史请求参数 |
| [WorkspaceController](../../backend/zflow-trigger/src/main/java/com/zgl/zflow/trigger/controller/WorkspaceController.java) | 工作区与广场 |
| [AdminController](../../backend/zflow-trigger/src/main/java/com/zgl/zflow/trigger/controller/AdminController.java) | 管理接口 |
| [Result](../../backend/zflow-types/src/main/java/com/zgl/zflow/types/result/Result.java) / [PageResult](../../backend/zflow-types/src/main/java/com/zgl/zflow/types/result/PageResult.java) | 返回包装 |
| [AuthInterceptor](../../backend/zflow-trigger/src/main/java/com/zgl/zflow/trigger/interceptor/AuthInterceptor.java) / [GlobalExceptionHandler](../../backend/zflow-trigger/src/main/java/com/zgl/zflow/trigger/handler/GlobalExceptionHandler.java) | HTTP 鉴权状态与业务错误 |
| [WebMvcConfig](../../backend/zflow-app/src/main/java/com/zgl/zflow/config/WebMvcConfig.java) | CORS 与拦截器范围 |
| [AdminService](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/admin/service/AdminService.java) | 配置分组、管理端校验与依赖检查 |
| [application-dev.yml](../../backend/zflow-app/src/main/resources/application-dev.yml) | 开发端口、context path、JWT 配置项 |
| [WorkspaceService](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/workspace/service/WorkspaceService.java) | MQ 异步行为、同步创建 |
| [WorkspaceRepository](../../backend/zflow-infrastructure/src/main/java/com/zgl/zflow/infrastructure/repository/WorkspaceRepository.java) | 更新 ID、MCP 清空语义 |
| [AiPlazaDao.xml](../../backend/zflow-infrastructure/src/main/resources/mapper/AiPlazaDao.xml) | 排序白名单与默认顺序 |
| [TaskService](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/ai/service/task/TaskService.java) | Cron 与 taskParam 消费方式 |
| [PersistUtil](../../backend/zflow-infrastructure/src/main/java/com/zgl/zflow/infrastructure/util/PersistUtil.java) | 消息数量校验阶段 |
| [AiSectionType](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/ai/model/enumeration/AiSectionType.java) | 29 个阶段类型 |
| [AbstractExecuteNode](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/ai/service/execute/AbstractExecuteNode.java) / [DispatchService](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/ai/service/dispatch/DispatchService.java) | SSE 消息、持久化与关闭 |
| [ArmoryMcpNode](../../backend/zflow-domain/src/main/java/com/zgl/zflow/domain/ai/service/armory/node/ArmoryMcpNode.java) | MCP 参数与超时单位 |

静态核验范围：125 条路由逐条对应源码，90 个结构共 506 个字段，9 个内部类型单独建模；JSON 示例、源码链接和文档锚点一并检查。已完成的鉴权、真实模型、向量库、MQ 等运行验证以及剩余范围见 [真实联调记录](LIVE-INTEGRATION.md)。

后续新增/修改 Controller、DTO 或流事件时，应同时更新接口清单、字段字典及本文对应流程；不要从预览数据反向定义接口。
