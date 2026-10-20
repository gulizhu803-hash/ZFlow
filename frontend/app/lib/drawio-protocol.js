export function editorUrl(configured, origin, configuredOrigin) {
  const url = new URL(configured || 'https://embed.diagrams.net/', origin);
  if (url.username || url.password || url.origin === origin || (url.origin !== 'https://embed.diagrams.net' && url.origin !== configuredOrigin)) throw new Error('编辑器地址须使用已配置的独立来源，以隔离账号信息');
  if (url.protocol !== 'https:' && !['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)) throw new Error('编辑器须使用 HTTPS，本机开发可使用 HTTP');
  for (const [key, value] of Object.entries({ embed: 1, proto: 'json', spin: 1, configure: 1, libraries: 0, saveAndExit: 0, noSaveBtn: 1, modified: 0, lang: 'zh', dark: 0 })) url.searchParams.set(key, value);
  return url;
}
export function editorMessage(event, origin, frameWindow) {
  if (event.origin !== origin || event.source !== frameWindow || typeof event.data !== 'string') return null;
  try { const message = JSON.parse(event.data); return typeof message.event === 'string' ? message : null; } catch { return null; }
}
