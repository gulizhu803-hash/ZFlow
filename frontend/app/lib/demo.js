export const strategies = {
  step: { label: '分步执行', color: 'sage', icon: 'workflow', description: '先规划，再行动。适合流程清晰的多步骤任务。', roles: ['工具检查', '任务规划', '逐步执行', '总结回复'] },
  loop: { label: '循环优化', color: 'peach', icon: 'repeat', description: '执行、评估、改进。让复杂任务逐轮接近目标。', roles: ['需求分析', '任务执行', '质量监督', '结果总结'] },
  react: { label: '自主探索', color: 'lavender', icon: 'sparkles', description: '观察与推理交替进行，灵活应对开放式问题。', roles: ['环境观察', '行动推理', '工具执行', '结果评估'] }
};
export const demoAgents = [
  { agentId: 'demo-research', repoId: 'demo-r1', agentName: '深度研究助手', agentDesc: '从一个问题出发，检索、分析并整理信息，形成有条理的研究报告。', agentType: 'step', icon: 'search', modelName: 'DeepSeek V3', tag: '研究与分析', createTime: '2026-09-10T09:30:00' },
  { agentId: 'demo-write', repoId: 'demo-r2', agentName: '灵感写作伙伴', agentDesc: '一起打磨你的下一个好点子，让零散的灵感成为清晰、有感染力的文字。', agentType: 'loop', icon: 'pen', modelName: 'Qwen Plus', tag: '内容创作', createTime: '2026-09-09T16:20:00' },
  { agentId: 'demo-code', repoId: 'demo-r3', agentName: '代码探索者', agentDesc: '理解项目结构，拆解复杂逻辑，帮助你更快找到问题与解决思路。', agentType: 'react', icon: 'code', modelName: 'DeepSeek V3', tag: '开发效率', createTime: '2026-09-09T11:00:00' }
];
export const demoClients = [{ clientId: 'demo-client', clientName: '通用助手', modelName: 'DeepSeek V3' }];
export const demoModels = [{ modelId: 'demo-model', modelName: 'DeepSeek V3', apiId: 'demo-api' }];
export const demoMcps = [{ mcpId: 'demo-search', mcpName: '网页检索', mcpDesc: '搜索公开网页，查找相关信息' }, { mcpId: 'demo-files', mcpName: '文件阅读', mcpDesc: '读取文本，提取关键信息' }];
export const demoRags = [{ ragTag: '产品与设计资料' }, { ragTag: '我的技术笔记' }, { ragTag: '灵感收集箱' }];
export const demoSessions = [
  { sessionId: 'demo-s1', sessionTitle: '探索 AI Agent 的产品设计趋势', sessionType: 'work', createTime: '2026-09-10T10:24:00' },
  { sessionId: 'demo-s2', sessionTitle: '让技术文档更容易被理解', sessionType: 'chat', createTime: '2026-09-10T09:16:00' },
  { sessionId: 'demo-s3', sessionTitle: '从零梳理一个新项目', sessionType: 'work', createTime: '2026-09-09T16:45:00' }
];
export const demoPlaza = [...demoAgents, { ...demoAgents[0], agentId: 'demo-meeting', agentName: '会议纪要整理师', agentDesc: '提取讨论重点与待办事项，让每次会议都带来清晰的下一步。', agentType: 'step', icon: 'list', tag: '日常办公' }, { ...demoAgents[1], agentId: 'demo-learn', agentName: '个人学习教练', agentDesc: '拆分学习目标，设计练习，一步一步建立你的知识体系。', agentType: 'react', icon: 'book', tag: '学习成长' }, { ...demoAgents[2], agentId: 'demo-review', agentName: '产品体验观察员', agentDesc: '从用户视角审视产品，发现细节问题并给出可落地的优化建议。', agentType: 'loop', icon: 'compass', tag: '产品设计' }].map((a, i) => ({ ...a, plazaId: 'demo-p' + i, templateId: 'demo-t' + i, plazaTitle: a.agentName, plazaDesc: a.agentDesc, userName: ['ZFlow 团队', '山野', '木白'][i % 3], likeCount: [128, 86, 64, 42, 37, 29][i], favorCount: [48, 32, 27, 15, 19, 11][i], commentCount: 0, liked: false, favored: false }));
export const sectionLabels = Object.fromEntries([
  ['analyzer', ['demand:任务需求分析', 'history:历史评估', 'strategy:执行策略', 'progress:完成度评估', 'status:分析状态']],
  ['performer', ['target:执行目标', 'process:执行过程', 'result:执行结果']], ['supervisor', ['issue:问题识别', 'suggestion:改进建议', 'score:质量评分', 'status:监督状态']],
  ['summarizer', ['overview:任务总结']], ['inspector', ['mcp:工具检查']], ['planner', ['step:任务规划']], ['runner', ['result:运行结果', 'status:运行状态']], ['replier', ['overview:任务回复']],
  ['observer', ['demand:目标解析', 'history:现状观察', 'judgement:继续判断', 'status:观察状态']], ['reasoner', ['target:下一步目标', 'action:下一步动作', 'acceptance:验收条件']],
  ['actor', ['target:执行目标', 'process:执行过程', 'result:执行结果']], ['evaluator', ['overview:最终结论']]
].flatMap(([role, items]) => items.map(item => { const [key, value] = item.split(':'); return [role + '_' + key, value]; })));
