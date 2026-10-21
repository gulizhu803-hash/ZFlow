# ZFlow 前后端提交规则

> 适用范围：所有前端、后端、文档、配置提交。  
> 每次提交前必须先读本文件。  
> 本文件默认不进入代码提交；如用户明确要求固化规则，单独做 `docs:` 提交，只提交本文件。

---

## 1. 第一原则：一次提交只做一层

一次 commit 只能是以下一种：

- 前端代码
- 后端代码
- 文档
- 基础设施 / 配置
- 测试基础设施

不允许：

- 前端 + 后端混在一个 commit
- 代码 + 设计文档混在一个 commit
- 功能 + 无关格式化混在一个 commit
- 多个不相关功能混在一个 commit

如果一次改动同时涉及前后端：

1. 先提交后端（或在后端分支提交）；
2. 记录后端 commit / API 契约；
3. 前端分支基于包含该后端能力的 feature 分支；
4. 前端 commit 只包含前端代码；
5. PR 描述里互相链接。

---

## 2. 分支模型

| 分支 | 用途 | 允许提交 |
| --- | --- | --- |
| `main` | 稳定基线 | 合并后的稳定内容；不直接堆开发提交 |
| `better-ui-1.0` | 前端 UI 分支，基于包含后端能力的 feature 分支 | 只允许前端代码 / 前端测试 |
| `fix/backend-hardening` | 后端 hardening 分支 | 只允许后端代码 / 后端测试 / 必要后端配置 |
| `feature/office-m1` | office 功能基线 | office 相关前后端，但提交仍要分层 |
| `feature/...` / `fix/...` | 短期功能分支 | 按分支主题提交 |
| `backup-...` | 备份分支 | 不用于新开发，不提交新内容 |

### 2.1 前端分支基线

前端分支不要默认基于 `main`。

如果功能依赖新后端接口、新字段、新 SSE 事件：

- 基于**已经包含该后端能力的 feature 分支**创建前端分支；
- 例如当前 UI 分支基于 `fix/backend-hardening`，而不是 `main`；
- 前端只在自己的 commit 里改 `frontend/`，不把后端提交带进来。

### 2.2 后端分支基线

后端分支：

- 基于 `main` 或后端集成分支；
- 不把前端页面改动、前端样式、前端文档带进后端 commit；
- 如果后端改动需要前端配合，在 PR 描述中写清楚契约，不要顺手改前端。

### 2.3 不要在备份分支开发

`backup-...`、`*-main-based` 只用于找回历史，不用于新提交。

提交前先确认：

```bash
git branch --show-current
```

如果当前分支是备份分支，停止，先切到正确分支。

---

## 3. 路径归属

### 前端代码

- `frontend/app/`
- `frontend/public/`
- `frontend/index.html`
- `frontend/vite.config.js`
- `frontend/package.json` / `package-lock.json`
- `frontend/styles/`
- `frontend/ui-v2-p0/src/`
- `frontend/ui-v2-p0/test/`
- `frontend/ui-v2-p0/fixtures/*.sse`

### 后端代码

- `backend/` 下的 Java、XML、SQL、配置
- `mcp/`
- 后端 Docker / 部署脚本
- 数据库迁移

### 文档

- `frontend/docs/`
- `docs/`
- 根目录 `README.md`
- `ui-v2-p0/README.md`
- `fixtures/README.md`
- 设计稿、效果图、过程记录

**文档默认不提交。** 只有用户明确要求时才单独做 `docs:` commit。

### 配置 / 基础设施

- `.github/`
- `perf/`
- `scripts/`
- `.gitignore`
- 根目录部署配置

配置改动单独提交，不和功能混在一起。

---

## 4. 提交前 10 项检查

每次提交前按顺序执行：

1. 确认分支正确
   ```bash
   git branch --show-current
   ```
2. 看工作区
   ```bash
   git status --short
   ```
3. 看改动范围
   ```bash
   git diff --stat
   ```
4. 只按路径暂存
   ```bash
   git add -- <明确的文件或目录>
   ```
5. 检查暂存清单
   ```bash
   git diff --cached --name-only
   ```
6. 检查空白错误
   ```bash
   git diff --cached --check
   ```
7. 跑对应测试 / 构建
8. 检查是否混入密钥、生成物、文档、无关文件
9. 写提交信息
10. 提交后确认
   ```bash
   git show --stat --oneline HEAD
   ```

任何一步发现不属于本层的文件，停止，取消暂存：

```bash
git restore --staged <path>
```

不要用 `git add .` 或 `git commit -a`。

---

## 5. 严禁提交的内容

- `.env`、`.env.local`、密钥、Token、Cookie、证书
- `node_modules/`
- `dist/`、`target/`、`build/`
- `.idea/`、`.vscode/`（除非团队明确共享）
- `.tmp/`
- 日志、覆盖率、临时截图
- 数据库真实数据、生产 dump
- 与本提交无关的格式化、重命名、import 排序
- 别人的未提交改动
- 设计稿、过程文档、会议记录（除非用户明确要求）

如果不确定一个文件是否该提交，**不要提交**，先问。

---

## 6. 提交信息规范

格式：

```text
<type>(<scope>): <短句>

<可选正文：为什么、影响、测试>
```

### type

- `feat`：新功能
- `fix`：修复
- `refactor`：重构，不改行为
- `test`：测试
- `docs`：文档
- `chore`：构建、依赖、工具
- `perf`：性能

### scope

- `ui`：前端 UI
- `web`：前端工程
- `api`：后端接口
- `backend`：后端服务
- `office`：office 功能
- `auth`：认证
- `infra`：部署 / 基础设施

### 例子

- `feat(ui): add run state adapter`
- `fix(ui): keep stop state after stream resolves`
- `test(ui): cover late stage after stop`
- `feat(api): add office run cancel endpoint`
- `fix(backend): avoid killing live peers on boot`
- `docs: add frontend/backend commit rules`

禁止：

- `update`
- `fix bug`
- `wip`
- `changes`
- 一个标题里写多个不相关功能

---

## 7. 测试与构建

### 前端

```bash
cd frontend
npm test
npm run build
```

P0 模块：

```bash
cd frontend/ui-v2-p0
npm test
```

### 后端

按改动模块执行：

```bash
mvn -pl <module> test
```

至少保证：

- 改动的模块能编译
- 相关测试通过
- 如果跳过测试，提交信息 / PR 里写明原因

### 文档

- 纯文档提交可以不跑代码测试
- 但必须确认没有混入代码

---

## 8. 混入文件的处理

如果工作区已有别人的改动：

- 不要 `git add .`
- 不要 `git reset --hard`
- 不要 `git clean -fd`
- 不要 `git checkout -- .`
- 不要替别人提交
- 只 `git add -- <你的文件>`

如果同一个文件里既有你的改动又有别人的改动：

- 用 `git add -p <file>` 只暂存你的 hunk
- 分不清就停止，先问
- 不要整文件提交

---

## 9. PR 规则

- 一个 PR 只做一层：前端或后端
- PR 标题和 commit 标题一致
- PR 描述写：
  - 改了什么
  - 为什么
  - 怎么验证
  - 影响的接口 / 页面
  - 截图（UI 改动）
- 前后端联动：
  - 后端 PR 先合或先说明契约
  - 前端 PR 链接后端 PR / commit
- 不把 `dist`、`target`、文档、截图源文件混进 PR
- 不在 PR 里顺手重构无关代码

---

## 10. AI / Agent 提交规则

任何 AI / Agent 提交前必须：

1. 读本文件。
2. 输出当前分支：
   ```bash
   git branch --show-current
   ```
3. 输出工作区：
   ```bash
   git status --short
   ```
4. 只按明确路径暂存。
5. 提交前输出：
   ```bash
   git diff --cached --name-only
   ```
6. 提交前跑测试并报告结果。
7. 提交后输出：
   ```bash
   git show --stat --oneline HEAD
   ```

Agent 禁止：

- `git add .`
- `git commit -a`
- `git reset --hard`
- `git clean -fd`
- `git checkout -- .`
- 未经允许 `git push --force`
- 未经允许 rebase 共享分支
- 未经允许 amend 已推送提交
- 把文档 / 过程记录混进代码提交

如果发现工作区混有无关改动：

- 只提交明确路径；
- 或停止并询问；
- 不要替用户清理。

---

## 11. 提交前自查表

- [ ] 当前分支正确，不在备份分支
- [ ] 只包含一层：前端 / 后端 / 文档 / 配置
- [ ] 没有 `git add .`
- [ ] `git diff --cached --name-only` 都是预期文件
- [ ] 没有密钥、`node_modules`、`dist`、`target`、`.tmp`
- [ ] 没有混入无关格式化
- [ ] 前端 / 后端测试通过
- [ ] 构建通过
- [ ] 提交信息符合 `type(scope): ...`
- [ ] 提交后 `git show --stat` 符合预期
- [ ] 没有提交文档（除非用户明确要求）

---

## 12. 判定标准

出现以下任一情况，**拒绝提交**：

- 前后端混在一起
- 代码和文档混在一起
- 暂存清单里有不认识的文件
- 工作区有别人的改动被一起暂存
- 测试失败且没有说明
- 提交信息无法说明改了什么
- 当前分支是备份分支
- 提交里包含密钥 / 生成物

一句话：

> **先确认分支，再确认路径，再确认测试，最后才 commit。**  
> **不确定就不提交，先问。**
