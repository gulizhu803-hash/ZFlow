// test/helpers/sse.mjs
// 读取原始 .sse 文本并解析为 [{ event, data }]。
// 只依赖 node:fs / node:url，不依赖 DOM / fetch。
// 正确处理：空行分隔事件、CRLF 换行、多行 data、注释行（: 开头）。

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

export function readSSEText(path) {
  const p = path instanceof URL ? fileURLToPath(path) : path;
  return readFileSync(p, 'utf8');
}

export function parseSSE(text) {
  const events = [];
  const lines = String(text ?? '').split(/\r?\n/);
  let data = [];
  let event = '';

  const flush = () => {
    // 只有带 data 的事件才产出结果；event 缺省为 'message'。
    if (data.length > 0) {
      events.push({ event: event || 'message', data: data.join('\n') });
    }
    data = [];
    event = '';
  };

  for (const raw of lines) {
    if (raw === '') {
      flush();
      continue;
    }
    if (raw.startsWith(':')) continue; // SSE 注释行。

    const colon = raw.indexOf(':');
    const key = colon < 0 ? raw : raw.slice(0, colon);
    let value = colon < 0 ? '' : raw.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1); // 去掉字段冒号后单个空格。

    if (key === 'data') data.push(value);
    else if (key === 'event') event = value;
    // 忽略 retry / id 等字段。
  }
  flush(); // 处理末尾没有空行的最后一个事件。

  return events;
}

export function loadSSE(path) {
  return parseSSE(readSSEText(path));
}
