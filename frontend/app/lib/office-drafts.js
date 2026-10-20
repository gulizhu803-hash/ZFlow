export const newKey = () => crypto.randomUUID();
export const unknownOutcome = error => !error.status || error.status >= 500 || error.errorKey === 'OFFICE_COMMIT_OUTCOME_UNKNOWN' || error.status === 401;
const prefix = 'zflow.office.draft.';
export function draftKey(owner, artifactId, version) { return prefix + [owner, artifactId, version].map(encodeURIComponent).join('.'); }
export function draftsFor(storage, owner) {
  const start = prefix + encodeURIComponent(owner) + '.', result = [];
  for (let i = 0; i < storage.length; i++) {
    const key = storage.key(i); if (!key.startsWith(start)) continue;
    try { const value = JSON.parse(storage.getItem(key)); if (value.owner === owner) result.push({ key, value }); } catch { /* Invalid copies are not restored. */ }
  }
  return result;
}
export function persistDraft(storage, owner, draft) {
  const key = draftKey(owner, draft.artifactId, draft.baseVersion);
  const copies = draftsFor(storage, owner), other = copies.filter(c => c.value.artifactId !== draft.artifactId);
  const value = { ...draft, owner }, serialized = JSON.stringify(value);
  if (other.length >= 3 || serialized.length + other.reduce((sum, c) => sum + JSON.stringify(c.value).length, 0) > 2_000_000)
    throw new Error('本机草稿容量已满，请先下载或处理旧草稿；当前修改仅保留在本页');
  storage.setItem(key, serialized);
  for (const copy of copies) if (copy.value.artifactId === draft.artifactId && copy.key !== key) storage.removeItem(copy.key);
}
export function removeDraft(storage, owner, artifactId) {
  for (const { key, value } of draftsFor(storage, owner)) if (value.artifactId === artifactId) storage.removeItem(key);
}
export function clearOwnerDrafts(storage, owner) { for (const { key } of draftsFor(storage, owner)) storage.removeItem(key); }
export function saveEnvelope(draft, xml, editEpoch) {
  return Object.freeze({ key: newKey(), artifactId: draft.artifactId, baseVersion: draft.baseVersion, xml, editEpoch });
}
export function acknowledgeSave(draft, envelope, result) {
  draft.baseVersion = result.version;
  draft.dirty = draft.editEpoch !== envelope.editEpoch;
  if (!draft.dirty) draft.xml = envelope.xml;
}
