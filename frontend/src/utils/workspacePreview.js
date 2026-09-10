// Clearly labelled, local-only design examples. Never inserted into a user store or sent to an API.
export const previewAgents = [
    { agentId: 'example-research', agentName: '深度研究助手', agentDesc: '拆解复杂问题，搜索信息，整理一份有条理的研究报告。', agentType: 'step', icon: 'globe', tone: 'mint' },
    { agentId: 'example-writing', agentName: '灵感写作搭档', agentDesc: '从一个想法开始，帮你构思、撰写与打磨每一段表达。', agentType: 'loop', icon: 'pen', tone: 'peach' },
    { agentId: 'example-code', agentName: '代码探索伙伴', agentDesc: '理解代码逻辑、定位问题，让开发思路更加清晰。', agentType: 'react', icon: 'code', tone: 'lavender' }
];
export const previewSessions = [
    { sessionId: 'example-session-1', title: '探索 RAG 的工作原理', sessionType: 'chat', createdAt: Date.now() - 3600000 },
    { sessionId: 'example-session-2', title: '为新项目整理技术调研', sessionType: 'work', createdAt: Date.now() - 7200000 },
    { sessionId: 'example-session-3', title: '把灵感变成一份行动计划', sessionType: 'chat', createdAt: Date.now() - 86400000 }
];
export const previewCards = [
    { id: 'p1', clientType: 'INSPECTOR', sectionType: 'inspector_analysis', sectionContent: '明确研究目标：理解 RAG 的基本流程，以及它适合解决的问题。将任务拆解为原理、流程和实践建议三个部分。', step: 1 },
    { id: 'p2', clientType: 'PLANNER', sectionType: 'planner_plan', sectionContent: '1. 梳理文档切分与向量化流程\n2. 解释语义检索与上下文增强\n3. 整理项目接入建议', step: 2 },
    { id: 'p3', clientType: 'RUNNER', sectionType: 'runner_result', sectionContent: '已整理知识处理、相似度检索和生成回答之间的关系，形成结构化说明。', step: 3 },
    { id: 'p4', clientType: 'REPLIER', sectionType: 'replier_overview', sectionContent: '研究内容已汇总，结果展示在右侧。', step: 4 }
];
export const previewWorkMessages = [
    { id: 'pw1', role: 'user', content: '帮我梳理 RAG 的工作原理和基本流程。' },
    { id: 'pw2', role: 'assistant', content: '## 让回答建立在你的知识之上\n\nRAG（检索增强生成）会先检索相关资料，再将资料与问题一起交给模型。\n\n### 一个完整的工作流程\n\n1. **准备知识**：将文档拆分成适合检索的片段。\n2. **建立索引**：把片段转换为向量并存入向量数据库。\n3. **检索内容**：根据问题找到语义相关的片段。\n4. **生成回答**：模型结合检索到的上下文组织答案。\n\n> 可以把它理解成：先查阅资料，再作答。\n\n### 在知流中开始\n\n进入 AI 对话，上传知识文件，选择对应知识库后即可提问。文档切分、模型质量与检索参数都会影响最终效果。' }
];
