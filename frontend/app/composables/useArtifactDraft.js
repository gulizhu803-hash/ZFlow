import { reactive } from 'vue';
import { persistDraft, draftsFor, removeDraft, saveEnvelope, acknowledgeSave, unknownOutcome } from '../lib/office-drafts.js';
export function useArtifactDraft(api, owner, onSaved) {
  const draft = reactive({ artifactId: '', baseVersion: 0, xml: '', editEpoch: 0, dirty: false, exportedEpoch: 0,
    saveState: 'idle', pending: null, error: '', report: null, currentVersion: null, persistedAt: null, storageWarning: '' });
  let generation = 0, exporting = false;
  function persist() {
    if (!draft.artifactId || !draft.xml || (!draft.dirty && !draft.pending)) return;
    try {
      persistDraft(sessionStorage, owner, { artifactId: draft.artifactId, baseVersion: draft.baseVersion, xml: draft.xml,
        editEpoch: draft.exportedEpoch, dirty: true, pending: draft.pending, updatedAt: Date.now() });
      draft.persistedAt = Date.now(); draft.storageWarning = '';
    } catch (e) { draft.persistedAt = null; draft.storageWarning = e.message || '存储不可用，此草稿仅保留在当前页面'; }
  }
  function begin(artifactId, version, xml, recovered) {
    generation++; exporting = false;
    Object.assign(draft, { artifactId, baseVersion: version, xml, editEpoch: 0, exportedEpoch: 0, dirty: false, saveState: 'idle', pending: null,
      error: '', report: null, currentVersion: null, persistedAt: null, storageWarning: '' });
    if (recovered) {
      Object.assign(draft, recovered, { saveState: recovered.pending ? 'outcomeUnknown' : 'idle', exportedEpoch: recovered.editEpoch });
      if (recovered.baseVersion !== version) { draft.currentVersion = version; if (!draft.pending) draft.saveState = 'conflict'; }
    }
  }
  function restore(artifactId) { try { return draftsFor(sessionStorage, owner).find(c => c.value.artifactId === artifactId)?.value; } catch { return null; } }
  function changed(xml) {
    draft.editEpoch++; draft.dirty = true;
    if (xml) { draft.xml = xml; draft.exportedEpoch = draft.editEpoch; persist(); }
  }
  async function capture(requestXml) {
    const ticket = generation, editEpoch = draft.editEpoch;
    const xml = await requestXml();
    if (ticket !== generation) throw new Error('文件已切换，已忽略旧的编辑器响应');
    if (!xml || !/<mxfile[\s>]/.test(xml)) throw new Error('编辑器未返回完整图表 XML，尚未备份最新修改');
    if (editEpoch >= draft.exportedEpoch) { draft.xml = xml; draft.exportedEpoch = editEpoch; persist(); }
    return { xml, editEpoch };
  }
  async function save(requestXml) {
    if (exporting || draft.saveState === 'saving' || (!draft.dirty && !draft.pending)) return false;
    const ticket = generation;
    draft.error = ''; draft.report = null;
    try {
      if (!draft.pending) {
        exporting = true; draft.saveState = 'saving';
        const snapshot = await capture(requestXml);
        if (ticket !== generation) return false;
        draft.pending = saveEnvelope(draft, snapshot.xml, snapshot.editEpoch); persist();
      }
      exporting = false; draft.saveState = 'saving';
      const envelope = draft.pending;
      const result = await api.saveRevision(envelope);
      if (ticket !== generation) return false;
      acknowledgeSave(draft, envelope, result); draft.pending = null; draft.saveState = 'idle';
      if (draft.dirty) persist(); else { try { removeDraft(sessionStorage, owner, draft.artifactId); } catch { draft.storageWarning = '版本已保存，本机旧草稿暂未清理'; } draft.persistedAt = null; }
      await onSaved(result); return true;
    } catch (e) {
      if (ticket !== generation) return false;
      draft.error = e.message;
      draft.saveState = unknownOutcome(e) && draft.pending ? 'outcomeUnknown' : e.errorKey === 'ARTIFACT_VERSION_CONFLICT' ? 'conflict' : e.status === 422 ? 'invalid' : 'failed';
      if (draft.saveState !== 'outcomeUnknown') draft.pending = null;
      draft.currentVersion = e.data?.currentVersion; draft.report = e.data?.validationReport;
      persist(); return false;
    } finally { if (ticket === generation) exporting = false; }
  }
  function discard() {
    generation++; try { removeDraft(sessionStorage, owner, draft.artifactId); } catch {}
    Object.assign(draft, { artifactId: '', xml: '', dirty: false, pending: null, saveState: 'idle', error: '', persistedAt: null });
  }
  function suspend() { persist(); generation++; }
  return { draft, begin, changed, capture, save, persist, restore, discard, suspend };
}
