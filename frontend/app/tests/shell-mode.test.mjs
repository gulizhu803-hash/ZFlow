import test from 'node:test';
import assert from 'node:assert/strict';
import { MODES, modeForPage } from '../lib/shell-mode.js';

test('modeForPage maps browse routes to browse', () => {
  for (const page of ['home', 'agents', 'knowledge', 'plaza', 'schedules', 'settings', 'admin', 'office']) {
    assert.equal(modeForPage(page), 'browse', `page=${page}`);
  }
});

test('modeForPage maps chat to focus and work to run', () => {
  assert.equal(modeForPage('chat'), 'focus');
  assert.equal(modeForPage('work'), 'run');
});

test('modeForPage falls back to browse for unknown or missing pages', () => {
  for (const page of ['', 'create', 'nope', 'unknown', null, undefined]) {
    assert.equal(modeForPage(page), 'browse', `page=${String(page)}`);
  }
});

test('MODES enumerates the four shell modes', () => {
  assert.deepEqual(MODES, ['browse', 'focus', 'run', 'create']);
});
