export const templates = [
  { id: 'architecture', type: 'diagram', icon: 'Workflow', name: '把复杂系统，画清楚', title: '订单系统架构图', description: '从需求出发，梳理服务与数据流。', tag: '架构图', prompt: '帮我梳理一个订单系统，包含网关、订单、支付、库存与数据库。', label: '服务架构' },
  { id: 'proposal', type: 'document', icon: 'FileText', name: '让想法，成为可执行的方案', title: '项目技术方案', description: '整理背景、技术选型与实施步骤。', tag: '文档', prompt: '帮我起草一份订单系统技术方案，包含目标、模块拆分和实施计划。', label: '技术方案' },
  { id: 'tasks', type: 'table', icon: 'Table2', name: '把计划，落到每一步', title: '项目任务清单', description: '拆解任务，让每个下一步都清晰。', tag: '表格', prompt: '将订单系统的开发工作拆成一份可编辑的任务清单。', label: '任务规划' },
];

export function sampleContent(type) {
  if (type === 'diagram') return {
    nodes: [
      { id: 'client', label: 'Web / 移动端', detail: '应用入口', x: 242, y: 24, w: 176, h: 58 },
      { id: 'gateway', label: 'API Gateway', detail: '鉴权 · 路由', x: 242, y: 126, w: 176, h: 58 },
      { id: 'orders', label: '订单服务', detail: 'Order Service', x: 44, y: 242, w: 164, h: 58 },
      { id: 'payment', label: '支付服务', detail: 'Payment Service', x: 248, y: 242, w: 164, h: 58 },
      { id: 'stock', label: '库存服务', detail: 'Inventory Service', x: 452, y: 242, w: 164, h: 58 },
      { id: 'mysql', label: 'MySQL', detail: '业务数据', x: 144, y: 358, w: 164, h: 58 },
      { id: 'redis', label: 'Redis', detail: '缓存 · 状态', x: 350, y: 358, w: 164, h: 58 },
    ],
    edges: [['client', 'gateway'], ['gateway', 'orders'], ['gateway', 'payment'], ['gateway', 'stock'], ['orders', 'mysql'], ['payment', 'mysql'], ['stock', 'redis']],
  };
  if (type === 'table') return {
    columns: ['开发任务', '所属模块', '优先级', '状态'],
    rows: [['确认核心业务流程', '需求分析', '高', '待开始'], ['设计订单数据结构', '订单服务', '高', '待开始'], ['实现订单创建接口', '订单服务', '高', '待开始'], ['接入支付结果通知', '支付服务', '中', '待开始'], ['完善库存扣减逻辑', '库存服务', '高', '待开始'], ['补充端到端验证', '质量保障', '中', '待开始']],
  };
  return { text: '# 订单系统技术方案\n\n本文件为可编辑的方案示例。请根据实际业务补充约束与验收标准。\n\n## 01 项目目标\n\n构建一个覆盖商品下单、库存校验与支付确认的订单服务，保证核心状态可查询、异常可恢复。\n\n## 02 模块与职责\n\n- API 网关：统一身份校验与请求路由。\n- 订单服务：维护订单状态与流转规则。\n- 支付服务：接收支付通知，处理重复回调。\n- 库存服务：负责库存预占、确认与释放。\n\n## 03 数据与一致性\n\n使用 MySQL 存储业务数据，Redis 承担缓存与短期状态。接口通过业务幂等键处理重复请求。具体事务边界需要结合部署方式确认。\n\n## 04 实施计划\n\n1. 明确接口契约与数据模型。\n2. 贯通创建订单到支付完成的最小流程。\n3. 验证重复请求、超时与异常恢复。\n4. 补充监控与发布前验收。' };
}

export function diagramPath(content, from, to) {
  const a = content.nodes.find(n => n.id === from), b = content.nodes.find(n => n.id === to);
  if (!a || !b) return '';
  const x1 = a.x + a.w / 2, y1 = a.y + a.h, x2 = b.x + b.w / 2, y2 = b.y;
  return `M${x1} ${y1} V${(y1 + y2) / 2} H${x2} V${y2 - 5}`;
}
const escapeXml = value => String(value).replace(/[<>&"']/g, c => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;', "'": '&apos;' })[c]);

export function diagramSvg(content) {
  const paths = content.edges.map(([a, b]) => `<path d="${diagramPath(content, a, b)}" stroke="#9ba5bf" fill="none" marker-end="url(#arrow)"/>`).join('');
  const nodes = content.nodes.map(n => `<g><rect x="${n.x}" y="${n.y}" width="${n.w}" height="${n.h}" rx="8" fill="white" stroke="#cbd2e0"/><text x="${n.x + n.w / 2}" y="${n.y + 25}" text-anchor="middle" font-size="14" fill="#202229">${escapeXml(n.label)}</text><text x="${n.x + n.w / 2}" y="${n.y + 43}" text-anchor="middle" font-size="10" fill="#6d7484">${escapeXml(n.detail)}</text></g>`).join('');
  return `<svg xmlns="http://www.w3.org/2000/svg" width="990" height="660" viewBox="0 0 660 440" font-family="Arial, sans-serif"><rect width="660" height="440" fill="#F7F7F8"/><defs><marker id="arrow" markerWidth="7" markerHeight="7" refX="5" refY="3.5" orient="auto"><path d="M0 0L6 3.5L0 7" fill="none" stroke="#9ba5bf"/></marker></defs>${paths}${nodes}</svg>`;
}
export function drawioXml(content) {
  const cells = content.nodes.map(n => `<mxCell id="${escapeXml(n.id)}" value="${escapeXml(n.label + '\n' + n.detail)}" style="rounded=1;whiteSpace=wrap;html=0;fillColor=#ffffff;strokeColor=#002FA7;" vertex="1" parent="1"><mxGeometry x="${n.x}" y="${n.y}" width="${n.w}" height="${n.h}" as="geometry"/></mxCell>`).join('');
  const edges = content.edges.map(([a, b], i) => `<mxCell id="edge-${i}" style="edgeStyle=orthogonalEdgeStyle;endArrow=block;" edge="1" source="${escapeXml(a)}" target="${escapeXml(b)}" parent="1"><mxGeometry relative="1" as="geometry"/></mxCell>`).join('');
  return `<mxfile host="ZFlow"><diagram name="架构图" id="zflow"><mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/>${cells}${edges}</root></mxGraphModel></diagram></mxfile>`;
}
export function downloadArtifact(artifact, format) {
  let content, extension, mime;
  if (artifact.type === 'diagram') { extension = format === 'drawio' ? 'drawio' : 'svg'; mime = extension === 'svg' ? 'image/svg+xml' : 'application/xml'; content = extension === 'svg' ? diagramSvg(artifact.content) : drawioXml(artifact.content); }
  else if (artifact.type === 'table') { extension = 'csv'; mime = 'text/csv;charset=utf-8'; content = '\uFEFF' + [artifact.content.columns, ...artifact.content.rows].map(row => row.map(v => { const text = String(v); return '"' + (/^[=+@\-\t\r\n]/.test(text) ? "'" : '') + text.replaceAll('"', '""') + '"'; }).join(',')).join('\r\n'); }
  else { extension = 'md'; mime = 'text/markdown;charset=utf-8'; content = artifact.content.text; }
  const url = URL.createObjectURL(new Blob([content], { type: mime }));
  const a = document.createElement('a'); a.href = url; a.download = `${artifact.title.replace(/[<>:"/\\|?*]/g, '_')}.${extension}`; a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export const typeInfo = { diagram: { label: '图表', icon: 'Workflow', extension: 'drawio' }, document: { label: '文档', icon: 'FileText', extension: 'md' }, table: { label: '表格', icon: 'Table2', extension: 'csv' }, chat: { label: '对话', icon: 'MessageSquare' } };
