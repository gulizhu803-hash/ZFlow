# P0 执行任务书：Office 产物扩展（XLSX + DOCX）

> 配套方案：同目录 `office-artifact-expansion.md`（已定稿 2026-10-28）。
> 用法：把「§1 通用前言」+ 任一「Tn」整段投喂给执行 agent。顺序：**T1 →（T2 ∥ T3）→ T4**。
> 监工（我）按 §7 检查单逐条验收，**不接受只看自报**。

---

## 1. 通用前言（每个 Tn 都要带上）

```text
你在 ZFlow 后端仓库里实现一个已明确定义的需求，不要扩大范围。

【代码位置】
- 后端根：D:/IdeaProject/ZFlow-backend/backend  （分支 backend/stability，6 个 Maven 模块：zflow-types / zflow-domain / zflow-infrastructure / zflow-trigger / zflow-api / zflow-app）
- 前端根：D:/IdeaProject/ZFlow-better-ui/frontend  （线上入口目录 frontend/app；frontend/src 是废弃旧版，禁止修改）
- 你要改的 Office 子系统：
  - 契约/入参：zflow-domain/src/main/java/com/zgl/zflow/domain/office/model/OfficeRequest.java
  - 生成网关：zflow-infrastructure/src/main/java/com/zgl/zflow/infrastructure/office/OfficeGenerationGateway.java
  - 规格渲染：zflow-infrastructure/src/main/java/com/zgl/zflow/infrastructure/office/DiagramSpecRenderer.java
  - 校验网关：zflow-domain/src/main/java/com/zgl/zflow/domain/office/gateway/IDiagramValidator.java
  - 存储网关：zflow-domain/src/main/java/com/zgl/zflow/domain/office/gateway/IArtifactStorage.java

【技术栈与构建（关键：本机没有 mvn / 没有 JDK17 / 没有 ~/.m2）】
- 项目：Java 17、Spring Boot 3.4.3、MyBatis、Maven 多模块
- 本机只有 IntelliJ 自带 JBR 25，且 ~/.m2 不存在 —— 编译**必须在容器里做**：
  # 快速编译（首次会下载全部依赖，5–15 分钟，需联网；zf-m2 是命名卷，会缓存）
  docker run --rm -v "D:/IdeaProject/ZFlow-backend/backend:/w" -w /w -v zf-m2:/root/.m2 \
    maven:3.9.9-eclipse-temurin-17 mvn -B -ntp -q -DskipTests compile
  # 跑指定单测
  docker run --rm -v "D:/IdeaProject/ZFlow-backend/backend:/w" -w /w -v zf-m2:/root/.m2 \
    maven:3.9.9-eclipse-temurin-17 mvn -B -ntp -Dtest=XxxTest test
- 若在 Git Bash 里执行上面命令，路径会被 MSYS 改写，务必前置 MSYS_NO_PATHCONV=1。
- 项目自带的 backend/Dockerfile 是多阶段构建（maven:3.9.9-eclipse-temurin-17），首次构建镜像较慢，**不要求**你构建镜像。

【必须遵守】
1. 只做本任务列出的改动；新增文件放在指定包路径下。
2. 不改 OfficeController 的 13 个端点签名，不改数据库表结构，不动 ai_office_run / ai_artifact 语义。
3. 不新增除 poi-ooxml 之外的第三方依赖（前端不新增任何依赖）。
4. 保持 DRAWIO 现有行为 100% 兼容（含 request_hash 幂等语义）。
5. 代码风格跟随现有文件（构造器注入 / record / static import 现有风格）。
6. 不 git commit、不切分支、不 push。

【完成后必须报回的证据（缺一不可）】
1. 改动文件清单 + git diff --stat
2. 编译命令与输出尾部（含退出码）；如跑了单测，附测试报告摘要（tests/pass/fail）
3. 每条验收标准逐条写「通过/未通过 + 依据」
4. 任何与本任务书不一致的决定 + 原因
```

---

## 2. T1（必须先做）：契约层 + 渲染接口骨架

```text
【Goal】
让 Office 请求支持多种产物类型（DRAWIO/XLSX/DOCX/PPTX），并把渲染/校验抽象成可注册的接口，为 T2/T3 铺路。P0 只实现 XLSX/DOCX 渲染，PPTX 只登记类型并返回明确错误。

【Boundary】
- 允许修改：OfficeRequest.java、新建 zflow-domain/.../office/gateway/IArtifactRenderer.java、IArtifactValidator.java、新建 zflow-infrastructure/.../office/render/ArtifactRenderers.java
- 允许新增测试：zflow-app/src/test/java/com/zgl/zflow/office/OfficeRequestArtifactTypesTest.java
- 不允许修改：OfficeController、数据库脚本、前端任何文件

【现状（已核实）】
- OfficeRequest.java:14 现在的闸门是 if (!List.of("DRAWIO").equals(artifactTypes)) throw ...
- OfficeRequest.java:17 校验 diagramType ∈ {FLOWCHART, ARCHITECTURE, SEQUENCE, ER}
- record 字段：sessionId, prompt, artifactTypes, diagramType, title, retryOfRunId；已有 normalize() / withoutRetry() / canonical() / requiredText(value,max,name)

【Constraints】
1. 把闸门改为白名单：SUPPORTED = Set.of("DRAWIO","XLSX","DOCX","PPTX")，要求 artifactTypes 非空且每个元素都在白名单内；错误信息要能指出非法值。
2. diagramType 仅在 artifactTypes 含 DRAWIO 时校验；其它类型忽略该字段。
3. 新增两个可选字段：theme（默认 default）、targetSlides（默认 0 表示不限）；只影响规范校验与 canonical()，不改任何端点签名。
4. **幂等兼容（硬要求）**：旧请求（只有 DRAWIO + diagramType + title）的 canonical() 输出必须与改动前**逐字相同**，否则 request_hash 变化会导致历史重试语义断裂。做法自选（例如新字段为空时不计入 canonical），并用单测把改动前的一次 canonical 输出作为断言常量。
5. 新增接口（最小面）：
   public interface IArtifactRenderer<T> {
       String type(); String contentType(); String fileExtension();
       T parseSpec(String json); byte[] render(T spec, OfficeRequest request);
   }
   public interface IArtifactValidator { String type(); void validate(byte[] content, OfficeRequest request); }
6. 新建 ArtifactRenderers（Spring 组件）持有 List<IArtifactRenderer<?>>，提供 rendererFor(String type)；PPTX 未实现时必须抛 OfficeException(503, "OFFICE_ARTIFACT_UNSUPPORTED", "该产物类型尚未实现")。
7. 不要动 IDiagramValidator 的现有实现（它继续负责 DRAWIO 校验），只新增 IArtifactValidator 抽象供后续使用。

【Acceptance Criteria】
- [ ] 容器内 compile 通过（退出码 0）
- [ ] 新增单测覆盖：DRAWIO 请求通过、XLSX/DOCX 请求通过、PPTX 请求走到「未实现」错误、脏类型（如 PDF）被拒并给出非法值
- [ ] 单测断言旧 DRAWIO 请求的 canonical() 与基线常量完全相同
- [ ] git diff 不包含 OfficeController / DB / 前端文件

【Required Evidence】
1. git diff --stat + 关键 diff（OfficeRequest 部分）
2. compile 与 test 的实际输出尾部 + 退出码
3. 基线 canonical 常量与改动后输出各一行，肉眼可对比
```

---

## 3. T2：XLSX 生成（spec + 渲染 + 校验 + fixture）

```text
【Goal】
让 artifactTypes 含 XLSX 的运行能产出一个**Excel 能打开**的 .xlsx，并可被 /office/artifacts/{id}/content 下载。

【Boundary】
- 允许新增：zflow-infrastructure/.../office/render/XlsxSpecRenderer.java、zflow-app/src/main/resources/office/sample-xlsx.json、zflow-app/src/test/java/com/zgl/zflow/office/XlsxSpecRendererTest.java
- 允许修改：pom.xml（仅在 zflow-infrastructure 或父 pom 加 poi-ooxml）、ArtifactRenderers 注册、OfficeGenerationGateway 的 fixture 分支
- 不允许修改：OfficeController、表结构、前端

【spec 契约（模型只产这个 JSON）】
{
  "title": "季度销售汇总",
  "tables": [
    { "title": "华东区", "headers": ["月份", "销售额", "同比"], "rows": [["1月", "120.5", "12%"], ["2月", "98.0", "-3%"]] }
  ]
}

【Constraints】
1. 依赖只加 org.apache.poi:poi-ooxml（版本选 5.x 与 Spring Boot 3.4.3 兼容者），不要引 easyexcel。
2. XlsxSpecRenderer：type()=XLSX；contentType()=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet；fileExtension()=.xlsx；parseSpec 严格反序列化，失败抛 OfficeException(422, "OFFICE_SPEC_INVALID", 明确原因)。
3. 渲染要求：每个 table 一个 sheet（sheet 名取 table.title，去非法字符、去重、≤31 字符）；第一行表头加粗、冻结首行；列宽自适应（按最长内容估算，上下限 8–50）；所有单元格按字符串写入以保留前导零（如需数字格式单独说明）。
4. 上限保护：单次最多 20 个 sheet、单 sheet 最多 50000 行、单文本 32767 字符，超限抛 OfficeException(422, "OFFICE_SPEC_TOO_LARGE", ...)。
5. 校验器：用 POI 把生成的字节重新打开，断言 sheet 数 = tables 数、每个 sheet 表头单元格文本与 headers 一致、每行列数 = headers 长度。
6. fixture 模式：新增 sample-xlsx.json，当 ZFLOW_OFFICE_GENERATION_MODE=fixture 且类型为 XLSX 时，用该样例 spec 走完整渲染链路（保证测试可复现、不调模型）。

【Acceptance Criteria】
- [ ] 容器内 compile 通过
- [ ] 单测：render 出的字节被 POI 重新打开成功，sheet/表头/列数断言全过
- [ ] 单测：非法 spec（缺 headers、rows 列数不齐）抛 OFFICE_SPEC_INVALID
- [ ] 单测：超限 spec 抛 OFFICE_SPEC_TOO_LARGE
- [ ] 生成的字节以 PK\x03\x04 开头（zip 魔数），大小 > 4KB

【Required Evidence】
1. git diff --stat（含 pom 变更）
2. 三个单测的实际输出 + tests/pass/fail 计数
3. 一次真实生成的字节数 + 前 4 字节十六进制 + POI 复读得到的 sheet 名列表
```

---

## 4. T3：DOCX 生成（spec + 渲染 + 校验 + fixture）

```text
【Goal】
让 artifactTypes 含 DOCX 的运行能产出 Word 能打开的 .docx。

【Boundary】
- 允许新增：zflow-infrastructure/.../office/render/DocxSpecRenderer.java、zflow-app/src/main/resources/office/sample-docx.json、zflow-app/src/test/java/com/zgl/zflow/office/DocxSpecRendererTest.java
- 允许修改：ArtifactRenderers 注册、OfficeGenerationGateway fixture 分支
- 不允许修改：OfficeController、表结构、前端

【spec 契约】
{
  "title": "项目周报",
  "sections": [
    { "heading": "本周进展", "heading_level": 1, "paragraphs": ["完成 A 模块"], "bullets": ["B 接口联调通过"], "table": { "headers": ["项", "状态"], "rows": [["A", "完成"]] } }
  ]
}

【Constraints】
1. 复用 T2 已引入的 poi-ooxml（XWPF）；不要再加依赖。
2. DocxSpecRenderer：type()=DOCX；contentType()=application/vnd.openxmlformats-officedocument.wordprocessingml.document；fileExtension()=.docx。
3. 渲染要求：文档标题用 Title 样式；section.heading 按 heading_level 生成 Heading 1..4；paragraphs 逐段；bullets 生成项目符号列表；table 生成带表头加粗的表格。
4. 中文字体：正文显式设置字体为 微软雅黑（东亚字体也要设置），避免不同机器渲染差异。
5. 校验器：POI 重新打开，断言段落数 ≥ sections 数、标题文本存在、表格单元格数与 headers 一致。
6. fixture：sample-docx.json，fixture 模式下走完整链路。

【Acceptance Criteria】
- [ ] 容器内 compile 通过
- [ ] 单测：字节被 POI 打开成功，段落/标题/表格断言全过
- [ ] 单测：非法 spec（sections 为空）抛 OFFICE_SPEC_INVALID
- [ ] 字节以 PK\x03\x04 开头，且能被 Word 打开（执行方如无法打开，至少要给出 POI 复读的结构摘要）

【Required Evidence】
1. git diff --stat
2. 单测输出 + tests/pass/fail
3. 生成字节数 + POI 复读得到的段落数/标题列表
```

---

## 5. T4：前端类型选择 + 下载呈现

```text
【Goal】
前端「智能办公」页支持选择产物类型（图/表格/文档），并在产物区按类型展示 + 下载。**本阶段不做在线预览。**

【Boundary】
- 允许修改：frontend/app/pages/Office.vue（提交运行处 :129 与 composer :166、空态文案 :175 附近）、必要时 frontend/app/lib/office-api.js
- 不允许修改：frontend/src/、后端任何代码；**不允许新增任何依赖**

【现状】
- Office.vue:129 构造 run body（当前写死 diagramType 等）；:166 是 composer 表单（文案「描述你想画的图」）；:175 空态文案提到「图表」
- 下载能力已存在：office-api.js 的 downloadRevision（blob + Content-Disposition 解析）
- 安全约束：office-api.js 的 safeOfficeUrl 只允许 <root>/office/ 下的 URL，不要绕过它

【Constraints】
1. composer 增加产物类型多选（图 DRAWIO / 表格 XLSX / 文档 DOCX），默认 DRAWIO；至少选一项才允许提交。
2. diagramType 选择器仅在选择了 DRAWIO 时显示。
3. 提交体：artifactTypes 传数组（如 ["XLSX"]），保持 sessionId/prompt/title 字段不变。
4. 产物区：按 artifact.type 显示类型徽标 + 文件大小 + 当前版本 + 下载按钮（对非 DRAWIO 类型不渲染 DrawioBridge，改为只读信息卡 + 下载）。
5. 文案同步：composer 的「描述你想画的图」改为按类型变化（选了表格就写「描述表格内容」）。
6. 不做预览：不要引 docx-preview/xlsx 渲染库。

【Acceptance Criteria】
- [ ] npm/npx 不可用时用 node 直接构建也通过：node node_modules/vite/bin/vite.js build 退出码 0
- [ ] 只选 XLSX 时请求体为 artifactTypes:["XLSX"] 且不含 diagramType
- [ ] 产物列表对 XLSX/DOCX 显示下载按钮，点击能触发下载（URL 仍在 /office/ 前缀下）
- [ ] 不新增依赖（package.json 无改动）

【Required Evidence】
1. Office.vue 的 git diff（完整）
2. 构建输出尾部 + 退出码
3. 你构造的两种请求体各一行（JSON 片段）
4. 说明非 DRAWIO 产物走哪条渲染分支（贴出行号）
```

---

## 6. 监工（我）的验收顺序

1. **边界检查**：`git -C D:/IdeaProject/ZFlow-backend status --porcelain` 与 `git diff --stat`，确认没碰 OfficeController / DB 脚本 / 前端（T4 除外）。
2. **编译**：容器内 `mvn -DskipTests compile`，退出码必须 0。
3. **单测**：`mvn -Dtest="OfficeRequestArtifactTypesTest,XlsxSpecRendererTest,DocxSpecRendererTest" test`，fail 必须 0。
4. **端到端**（我起宿主 jar，用真后端跑）：
   - `POST /office/sessions` → sessionId
   - `POST /office/runs`（`Idempotency-Key` 必带）`{artifactTypes:["XLSX"], prompt:"..."}` → 轮询到 `SUCCEEDED`
   - `GET /office/artifacts?sessionId=...` → `type=xlsx`
   - `GET /office/artifacts/{id}/content` → 断言 `content-type` 正确、字节 >4KB、前 4 字节 `50 4B 03 04`，并用 python/POI 解开确认存在 `xl/workbook.xml`（docx 对应 `word/document.xml`）
5. **DRAWIO 回归**：`artifactTypes:["DRAWIO"]` 仍 `SUCCEEDED`，content 仍是 `<mxfile`。
6. **幂等回归**：同一 `Idempotency-Key` 重放两次 → 第二次 `replayed:true` 且 runId 相同。
7. **前端**：`node node_modules/vite/bin/vite.js build` 退出码 0；`package.json` 未变。

任何一条不过 → 带着**具体证据**退回重派，不接受「我这边是好的」。

---

## 7. 备注（给执行方的一次性提醒）

- 本机 `npm`/`npx` 在 bash 里不可直接执行，前端一律用 `node node_modules/vite/bin/vite.js <cmd>`。
- 首次 Maven 运行会下载整个依赖树（5–15 分钟），别以为是卡死；用命名卷 `zf-m2` 缓存可复用。
- 容器 `miniagent-backend-1` 里跑的 jar 是旧的（stability @8657f05），你的改动**不会**自动进那个容器；联调请按方案文档 §8.1 在宿主起 jar，或自行 `docker build` 后替换。
- 别动 `ai_office_run` / `ai_artifact` 表结构；产物类型靠现有 `artifact_type` 列区分即可。

---

## 8. T1 验收结论（2026-10-28，监工）

**结论：有条件通过（CONDITIONAL PASS）** —— 契约与接口层质量合格，但有一条**承诺未兑现**必须补丁后才能进入 T2/T3。

### 8.1 已核实通过的部分

| 检查项 | 我的证据 |
|---|---|
| 容器内编译 | `mvn -DskipTests compile` → **EXIT=0**，6 个模块 BUILD SUCCESS（1 分 29 秒） |
| T1 单测 | `-Dtest=OfficeRequestArtifactTypesTest` → **Tests run: 13, Failures: 0, Errors: 0**，BUILD SUCCESS |
| 边界 | `git status` 仅 2 改 4 新：`OfficeRequest.java`、`OfficeRequests.java`、两个新接口、`render/ArtifactRenderers.java`、测试类；**未碰** OfficeController / DB 脚本 / 前端 |
| 白名单闸门 | `SUPPORTED_ARTIFACT_TYPES = DRAWIO/XLSX/DOCX/PPTX`；脏类型报 422 `UNSUPPORTED_ARTIFACT_TYPE` 且错误信息带非法值 |
| diagramType 条件校验 | 仅含 DRAWIO 时校验；非 DRAWIO 请求置 null（单测覆盖） |
| 幂等兼容 | 基线常量 + `OfficeJson` 用 `LinkedHashMap` + `WriteMapNullValue`（键序与 null 语义稳定）→ 旧 DRAWIO 请求 canonical 逐字不变，可信 |
| 注册表质量 | 重复注册抛 `IllegalStateException`；类型 key 统一大写去空格；未实现统一 503 `OFFICE_ARTIFACT_UNSUPPORTED` |
| 越界改动 | `OfficeRequests.java`（api 层）加 `theme/targetSlides` 解析 —— 超出任务书 Boundary，但**必要且正确**（否则 `fields()` 白名单会拒收新字段）。已接受 |

### 8.2 阻塞项：注册表是死代码，未实现类型会静默产出 DRAWIO

| 证据 | 说明 |
|---|---|
| `ArtifactRenderers` / `rendererFor` 仅出现在注释里，**无任何生产代码注入** | grep 全仓：除自身与单测外 0 处引用 |
| `artifactTypes()` 只被 `OfficeViews`（响应回显）读取 | 运行路径完全不看请求的产物类型 |
| `OfficeRunExecutor.java:76` `artifact.setArtifactType("DRAWIO")` | 产物类型硬编码 |
| `OfficeRunExecutor.java:80`、`ArtifactService.java:42` `setContentType("application/xml")` | 内容类型硬编码 |
| `OfficeController.java:106` `contentType(MediaType.APPLICATION_XML)` | 下载响应头硬编码 |

**后果**：T1 之后 `artifactTypes:["PPTX"]`（以及暂时未实现的 `XLSX`/`DOCX`）能通过校验进入流水线，**产出一张 drawio 图**并被记为 `type=DRAWIO`，而不是契约承诺的 503。单测只证明了"注册表会抛"，没证明"系统会抛"。

### 8.3 T1 补丁任务书（发给执行方）

```text
【Goal】
让"未实现的产物类型"在**运行入口**被拒绝，兑现 503 OFFICE_ARTIFACT_UNSUPPORTED 的契约；DRAWIO 行为保持不变。

【Boundary】
- 允许修改：zflow-domain/.../office/service/OfficeRunService.java（入口处做能力探测）
- 允许新增测试：zflow-app/src/test/java/com/zgl/zflow/office/OfficeRunArtifactCapabilityTest.java
- 不允许修改：OfficeController、OfficeRunExecutor、ArtifactService、DB、前端

【现状】
- ArtifactRenderers 是 Spring @Component（zflow-infrastructure/.../office/render/），但没有任何生产代码注入它
- 渲染接口 IArtifactRenderer 定义在 zflow-domain/.../office/gateway/（domain 可以依赖它，实现由 infrastructure 提供）
- 目前 DRAWIO 走老的 DiagramSpecRenderer 路径，没有 IArtifactRenderer 实现

【Constraints】
1. 在**创建 run 之前**（OfficeRunService 的提交入口）对 request.artifactTypes() 逐个探测：DRAWIO 跳过（豁免，走老路径）；其它类型必须能从 List<IArtifactRenderer<?>> 中找到实现，否则抛 OfficeException(503, "OFFICE_ARTIFACT_UNSUPPORTED", "该产物类型尚未实现：" + type)。
2. 注入方式：构造器注入 List<IArtifactRenderer<?>>（domain 侧使用自己的 gateway 接口，不要依赖 infrastructure 的 ArtifactRenderers 类）。
3. 拒绝必须发生在**落库之前**：不得留下 status=QUEUED 的孤儿 run。
4. DRAWIO 回归：`artifactTypes:["DRAWIO"]` 行为与 request_hash 完全不变。
5. 不要顺手实现 XLSX/DOCX（那是 T2/T3）。

【Acceptance Criteria】
- [ ] 容器内 compile 退出码 0
- [ ] 新增单测：artifactTypes 含 PPTX 时提交入口抛 503 OFFICE_ARTIFACT_UNSUPPORTED；含 DRAWIO 时不抛
- [ ] 新增单测：混合类型 ["DRAWIO","PPTX"] 也被拒（有一个不可用即整体拒绝）
- [ ] 真机：`POST /office/runs` 传 ["PPTX"] → HTTP 503 + errorKey=OFFICE_ARTIFACT_UNSUPPORTED；`ai_office_run` 无新增行

【Required Evidence】
1. git diff（OfficeRunService 部分）
2. compile/test 输出尾部 + 退出码
3. 真机两次请求的响应体（PPTX 被拒 / DRAWIO 成功）
```

### 8.4 追加到 T2/T3 的硬性要求（T1 验收时发现）

T2/T3 落地时**必须**把这四处硬编码改成类型驱动，否则 XLSX/DOCX 会被存成 `type=DRAWIO` / `application/xml`：

| # | 位置 | 改法 |
|---|---|---|
| 1 | `OfficeRunExecutor.java:76` | `artifact.setArtifactType(type)`（取 request.artifactTypes() 的单一类型；多类型需按类型分别落产物） |
| 2 | `OfficeRunExecutor.java:80` | `revision.setContentType(renderer.contentType())` |
| 3 | `ArtifactService.java:42` | 保存新版本时按 artifact 的类型取 contentType（图表 → application/xml） |
| 4 | `OfficeController.java:106` | `contentType(MediaType.parseMediaType(file.revision().getContentType()))`（**只改这一处实现，不改端点签名**） |

> 说明：T2/T3 任务书 §3/§4 里写的"不允许修改 OfficeController"据此放宽为"仅允许改 `file()` 的 contentType 解析，不得改端点签名与路径"。