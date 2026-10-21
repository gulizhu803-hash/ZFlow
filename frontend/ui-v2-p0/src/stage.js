// stage.js
// ZFlow「Quiet Canvas 2.0」P0 stage 归一化：把任意 SSE entity 归一为稳定的 stage 形状。
// 纯函数，不修改入参 entity，缺字段给安全默认值，不抛错。
// 同一条 entity 多次 normalize 结果稳定（id 由确定性规则生成，不依赖随机数/时钟）。

// 把任意值安全地转为字符串；null/undefined 给 ''。
function asString(value) {
  if (value == null) return '';
  return typeof value === 'string' ? value : String(value);
}

// 可选数字字段：null/undefined/''/非数字 → undefined；其余归一为有限 number。
function toOptionalNumber(value) {
  if (value == null || value === '') return undefined;
  const n = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(n) ? n : undefined;
}

// 稳定 id：实体自带 id 优先；否则由 clientType/sectionType/round/step 确定性生成。
// 用 djb2 哈希保证任意字符都稳定、紧凑，且不依赖 Math.random / Date.now。
function stableId(entity) {
  const seed = [
    asString(entity.clientType),
    asString(entity.sectionType),
    entity.round == null ? '' : String(entity.round),
    entity.step == null ? '' : String(entity.step)
  ].join('\u0000');
  let hash = 5381;
  for (let i = 0; i < seed.length; i += 1) {
    hash = ((hash << 5) + hash + seed.charCodeAt(i)) >>> 0;
  }
  return 'stage-' + hash.toString(36);
}

export function normalizeStage(entity, at) {
  // 非对象（null/undefined/字符串等）当作空对象处理，保证不抛错。
  const source = entity != null && typeof entity === 'object' ? entity : {};

  const clientType = asString(source.clientType);
  const sectionType = asString(source.sectionType);
  // label 回退：sectionTitle → sectionType → clientType → ''。
  const label = asString(source.sectionTitle) || sectionType || clientType;

  const ownId = source.id == null ? '' : asString(source.id).trim();
  const id = ownId !== '' ? ownId : stableId(source);

  return {
    id,
    clientType,
    sectionType,
    label,
    round: toOptionalNumber(source.round),
    step: toOptionalNumber(source.step),
    content: asString(source.sectionContent),
    at: at == null ? null : at,
    raw: entity
  };
}
