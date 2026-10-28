# ZFlow 办公产物扩展方案（PPT / 表格 / 文档）

> 参考实现：**WaLiOffice**（`D:\IdeaProject\walioffice`，Rust + axum + React 18）。本文件是**方案**，不含实现代码。
> 基线：ZFlow `backend/stability@8657f05` 的 Office 子系统 + `better-ui-1.0` 前端。写作日期：2026-10-27。
> **状态：已定稿（2026-10-28）**，三项决策见 §7；执行任务书见同目录 `P0-execution-brief.md`。

## 0. 结论先说

| 决策点 | 推荐 | 一句话理由 | 什么会推翻它 |
|---|---|---|---|
| 产物范围 | **P0 先做 XLSX + DOCX，P1 再做 PPTX** | XLSX/DOCX 用 POI 是一天量级、无版式难题；PPTX 是版式工程，风险差一个数量级 | 你的真实需求是必须能出 PPT，那就先 PPTX |
| 渲染路线 | **Java 侧 Apache POI（XSLF/XWPF/XSSF）** | ZFlow 是 Spring 单体、已有 Maven 与产物治理；POI 一个依赖覆盖三种格式 | 需要像素级自定义版式、或不允许加大依赖 → 学 WaLiOffice 手写 OOXML |
| 生成结构 | **沿用 ZFlow 已有两段式（spec → 服务端渲染）** | `ZFLOW_OFFICE_OUTPUT_MODE=spec` + `DiagramSpecRenderer` 已是这套，扩展即可 | 若想让模型直出文件字节（不推荐：无法校验、无法版本化） |
| 版式由谁定 | **内容 LLM 出、版式代码定**（WaLiOffice 核心经验） | 让模型决定坐标会把失败率推高，WaLiOffice 本身也没这么做 | 无 |

工期粗估（1 人）：P0 XLSX+DOCX ≈ 2–3 天；P1 PPTX ≈ 4–6 天；P2 前端呈现 ≈ 2 天。

---

## 1. ZFlow 现状证据（决定改哪里、不用改什么）

| 事实 | 证据 | 影响 |
|---|---|---|
| 只允许一种产物 | `OfficeRequest.java:14`：`if (!List.of("DRAWIO").equals(artifactTypes)) throw ...` | 唯一必须改的硬闸门 |
| 图类型已 4 种 | `OfficeRequest.java:17`：`Set.of("FLOWCHART","ARCHITECTURE","SEQUENCE","ER")` | 新增产物时要保证 `diagramType` 仅对 DRAWIO 生效 |
| **已有两段式雏形** | `application.yml` 的 `zflow.office.output-mode: ${ZFLOW_OFFICE_OUTPUT_MODE:xml}`（注释：xml=模型直出 drawio XML；**spec=只出结构 JSON，服务端渲染**）+ `zflow-infrastructure/.../DiagramSpecRenderer.java` | 扩展新格式不需要发明新流水线，只加 spec schema + renderer |
| 运行治理已完备 | `ai_office_run`（历史 119 条 / 事件 809 条）、`ai_artifact.artifact_type`、`ai_artifact_revision`、`OfficeController` 13 个端点（含 `/content`、`/download`、`/revisions`） | **不需要**再设计版本/幂等/事件，直接复用 |
| 校验器是按图写的 | `IDiagramValidator` + `DiagramValidationReport` | 需要泛化出按类型的校验（P0 必做） |
| 存储抽象已在 | `IArtifactStorage`（gateway） | 新格式只增加 content-type 与扩展名 |

**一句话**：ZFlow 缺的不是办公流水线，而是「另外三种产物的 spec 定义 + renderer + validator + 前端呈现」。

---

## 2. WaLiOffice 可复用清单（逐项带位置）

| # | 可复用的设计 | WaLiOffice 位置 | ZFlow 落点 |
|---|---|---|---|
| 1 | **一个产物一个工具**：`ppt_plan`/`ppt_generate`/`doc_generate`/`sheet_generate`/`chart_generate`/`drawio_generate`/`md_generate`/`image_prompt`/`video_*` | `server/src/agent/tools/mod.rs:1-14` | `IOfficeGenerationGateway` 按 `artifactTypes` 分派到 Diagram/Sheet/Doc/Ppt 生成器 |
| 2 | **意图关键词表**：PPT=`ppt/演示文稿/幻灯片/presentation/汇报材料`；表格=`excel/xlsx/表格/数据分析/排期/预算/csv`；文档=`word文档/docx/报告/prd/方案书` | `server/src/agent/intent.rs:26,275,282,296,303` | ZFlow `/ai/chat` 若要一句话决定产物，可照抄这张表当兜底路由词典 |
| 3 | **PPT 两段式契约** | `agent/tools/ppt_plan.rs:137-138` plan 阶段只返回严格 JSON：`{"title":"PPT标题","slides":[{"title","layout","goal","visual","points":[]}]}`；`ppt_generate.rs:26-30` 消费 `PresentationPlan` | 直接作为 ZFlow `PPTX spec` 的 JSON schema（加 `theme` 字段） |
| 4 | **内容由 LLM、版式由代码**（最关键） | `models/mod.rs:143-180` `SlideElement{x,y,w,h,type,text,font_size,color,bold,fill,shape,rows,cols,table_data,chart_type,chart_data}`；`ppt_generate.rs:40` `get_theme_palettes(theme)` → default/business/tech/warm/minimal | ZFlow 侧同理：模型只产标题/要点/可视化建议/版式模板名，坐标与配色由 Java 常量表算 |
| 5 | **PPTX = 手写 OOXML + zip**（可完全不引重库） | `render/pptx_render.rs`（357 行）：11 个静态部件 `[Content_Types].xml`、`_rels/.rels`、`docProps/{app,core}.xml`、`ppt/presentation.xml`+rels、`ppt/theme/theme1.xml`、`ppt/slideMasters/slideMaster1.xml`+rels、`ppt/slideLayouts/slideLayout1.xml`+rels，外加每页 `slideN.xml`+rels；16:9 用 EMU `12192000×6858000` | 选 POI XSLF 可跳过；选手写路线时这就是部件清单底稿 |
| 6 | **DOCX 结构** | `render/docx_render.rs:5-16` `DocSection{heading, heading_level, paragraphs[], bullets[], table}`（105 行，docx-rs） | POI XWPF 同构：标题层级 / 段落 / 项目符号 / 表格 |
| 7 | **XLSX 结构** | `render/xlsx_render.rs:5-15` `SheetData{title, tables:[{title, headers[], rows[][]}]}`（82 行，rust_xlsxwriter） | POI XSSF 同构：多 sheet + 表头 + 行数据 |
| 8 | **产物契约** | `agent/tool.rs:76` `ToolArtifact{kind: document/ppt/drawio/sheet/image/code/mixed}`；`ToolResult.artifacts`；`ToolContext.prior_artifacts`；SSE 下发与会话合并 `routes/chat.rs:35,326,489-537` | ZFlow 已有 `OfficeArtifact`，只需把 `type` 扩成 `DRAWIO/XLSX/DOCX/PPTX` |

---

## 3. ZFlow 改造方案（按层）

### 3.1 契约层

```java
// 现在
if (!List.of("DRAWIO").equals(artifactTypes)) throw OfficeException.invalid("artifactTypes 不合法");
// 改为
private static final Set<String> SUPPORTED = Set.of("DRAWIO", "XLSX", "DOCX", "PPTX");
if (artifactTypes == null || artifactTypes.isEmpty() || !SUPPORTED.containsAll(artifactTypes)) throw ...;
// diagramType 仅在含 DRAWIO 时校验；PPT 专属字段（theme/targetSlides）按类型校验
```

- `OfficeRequest` 增可选字段：`theme`（PPT 主题）、`targetSlides`（期望页数），全部在 `normalize()` 给默认值，**旧 DRAWIO 请求 100% 兼容**。
- 幂等无需改机制：`canonical()` 包含新字段即可参与 `request_hash`。

### 3.2 生成层（两段式，复用现有 spec 通道）

| 产物 | spec 阶段（模型出 JSON） | 渲染阶段（服务端） | 校验阶段 |
|---|---|---|---|
| DRAWIO | 已有 | 已有 | 已有 `IDiagramValidator` |
| XLSX | `{title, tables:[{title, headers[], rows[][]}]}` | 新增 `XlsxSpecRenderer`（POI XSSF） | zip 合法、sheet≥1、每行列数与表头一致 |
| DOCX | `{title, sections:[{heading, heading_level, paragraphs[], bullets[], table?}]}` | 新增 `DocxSpecRenderer`（POI XWPF） | 标题/段落存在、非空文档 |
| PPTX | `{title, theme, slides:[{title, layout, goal, visual, points[]}]}`（照抄 `ppt_plan` schema） | 新增 `PptxSpecRenderer`（POI XSLF）；版式由 `SlideLayout` 常量表决定（title/content/two-column/section） | slide≥1、每页文本非空、总字数上限 |

接口建议（最小改动）：

```java
public interface IArtifactRenderer<T> {
    String type();                              // DRAWIO/XLSX/DOCX/PPTX
    String contentType();                       // application/vnd...
    String fileExtension();                     // .drawio/.xlsx/.docx/.pptx
    T parseSpec(String json);                   // 严格反序列化 + 业务校验
    byte[] render(T spec, OfficeRequest req);   // 纯函数，不做 IO
}
```

`OfficeGenerationGateway` 内部按 `artifactTypes` 选 renderer；`fixture` 模式沿用（每格式放一份内置样例 spec，保证测试可复现）。

### 3.3 校验层

`IDiagramValidator` 泛化为 `IArtifactValidator`：DRAWIO 保留 mxGraph 检查；其它三种用「开包 + 关键结构计数」（POI 重新读一遍，成本低、能挡 90% 坏产物）。校验结果仍写进现有 `generation_receipt`，**不新增表**。

### 3.4 存储 / 版本 / 事件

**不改**。`ai_artifact.artifact_type` + `ai_artifact_revision` + `/content` `/download` `/revisions` 都是类型无关的；只需按 renderer 输出 `content_type` 与文件名后缀。

### 3.5 前端（`frontend/app` 的 Office 页）

| 改动 | 说明 |
|---|---|
| 类型选择 | 新建运行时 `artifactTypes` 从硬编码改成多选（图/表格/文档/PPT） |
| 预览 | 目前只渲染 drawio XML；XLSX/DOCX/PPTX 首版走「下载 + 只读元信息卡片」 |
| 下载 | 复用 `office-api.js` 的 `downloadRevision`（已支持 blob + Content-Disposition） |
| 注意 | `safeOfficeUrl()` 强制 URL 落在 `<root>/office/` 下；新格式复用已存在的 content/download 端点，**无需放开白名单** |

---

## 4. 渲染技术选型（含否决理由）

| 格式 | 推荐 | 备选 | 否决/代价 |
|---|---|---|---|
| XLSX | **POI `XSSFWorkbook`**（poi-ooxml） | EasyExcel（大表流式强、复杂样式弱） | 手写 OOXML：部件比 docx 多，性价比低 |
| DOCX | **POI `XWPFDocument`** | docx4j（功能全但依赖重） | 手写可行但 POI 已成熟 |
| PPTX | **POI `XSLFSlide`**（首版） | WaLiOffice 式手写 OOXML+zip（357 行已验证） | POI XSLF 对异形/像素级版式支持弱；产品要求强设计感时再切手写 |

依赖影响：`poi-ooxml` ≈ 10–12 MB（含 poi、xmlbeans、commons-compress）。`zflow-app.jar` 已 129 MB，可接受；但需确认镜像体积与启动时间。

---

## 5. 分阶段交付与验收

### P0（2–3 天）XLSX + DOCX

- 契约支持 `XLSX`/`DOCX`（旧 `DRAWIO` 不受影响）
- 新增 `XlsxSpecRenderer`、`DocxSpecRenderer`、两个 validator、两份 fixture spec
- 前端类型复选 + 下载
- **验收**：`POST /office/runs` 传 `artifactTypes:["XLSX"]` → `SUCCEEDED` → `/artifacts` 出现 `type=xlsx` → `/content` 下载文件能被 Excel 打开且列数一致；DOCX 同理可被 Word 打开；`DRAWIO` 回归不退化

### P1（4–6 天）PPTX

- 照抄 `ppt_plan` schema 做 spec；`PptxSpecRenderer` 起步支持 4 种版式 + 3 套主题
- **验收**：输入「做个季度汇报 PPT，8 页」→ 产物可被 PowerPoint/WPS 打开、页数=8、标题页+目录页+内容页结构正确、中文不乱码

### P2（2 天）前端呈现

- 产物列表按类型显示图标/大小/版本；提供「重新生成（retryOfRunId）」入口

---

## 6. 风险与明确不做的事

| 风险 | 处置 |
|---|---|
| 中文字体乱码（PPTX 尤其） | 显式设置 fontFamily（思源黑体/微软雅黑），并用 WPS + PowerPoint 各开一次 |
| POI 与 Spring Boot 的 xmlbeans 冲突 | 先 `mvn dependency:tree` 检查；POI 5.x + SB3 兼容 |
| 产物体积/磁盘增长 | 复用现有 storage-root 与租约清理；单次上限（pptx ≤ 60 页、xlsx ≤ 5 万行） |
| 模型输出不合 schema | 沿用 DRAWIO 做法：严格反序列化失败即 FAILED + 明确 error_code（如 `OFFICE_SPEC_INVALID`），不静默降级 |

**明确不复制 WaLiOffice 的部分**：
1. 它的 SQLite + 单二进制 + rust-embed 部署模型（ZFlow 已有更完整的运行治理）；
2. 它的意图关键词路由（ZFlow 的 `/ai/chat` 已是模型驱动工具链，关键词仅作可选兜底）；
3. 它的无幂等/无租约运行层——ZFlow 现有 `Idempotency-Key` + `LEASE_EXPIRED` 回收更完整，**不要为对齐参考实现而降级**。

---

## 7. 已决策（2026-10-28 定稿）

| # | 决策 | 结论 | 落在哪 |
|---|---|---|---|
| 1 | 范围 | **P0 = XLSX + DOCX**；PPTX 放 P1 | P0 任务书 T1–T4 |
| 2 | PPTX 渲染路线 | **模板法（POI 读 .pptx 模板 + 占位符替换）** 为主，POI XSLF 动态补页为兜底；手写 OOXML 仅在「零新依赖」或「像素级自绘」两个条件下重启讨论 | P1 |
| 3 | 在线预览 | **首版只做下载 + 元信息卡片**；图表类产物复用已有 `echarts` 做页内渲染（零新依赖）；**PPTX 不做浏览器预览**（无可靠前端方案，转 PDF 属另一量级决策） | P2 |

定稿依据：① XLSX/DOCX 能一次性把契约层、渲染接口、校验泛化、fixture 基线、前端多选这五件通用件建好并验证，且与版式无关；② PPT 的质量瓶颈是版式而非代码，模板法把版式交给设计师，工期从 4–6 天降到 2–3 天；③ 预览的真实痛点是「下载后打不开」而非「页内看不见」，PPTX 前端无可靠渲染器，硬上等于引入长期运维负担。

*WaLiOffice 行号取自 `D:\IdeaProject\walioffice@50ad355`（比 `plante\walioffice@1238829` 新 11 个提交）；ZFlow 行号取自 `backend/stability@8657f05`。*