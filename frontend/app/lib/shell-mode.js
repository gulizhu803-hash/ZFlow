// Four-mode app shell — P1 stage 2.
// Maps a route page id to one of the shell modes. Pure, no imports.

export const MODES = ['browse', 'focus', 'run', 'create'];

const PAGE_MODES = {
  home: 'browse',
  agents: 'browse',
  knowledge: 'browse',
  plaza: 'browse',
  schedules: 'browse',
  settings: 'browse',
  admin: 'browse',   // reuse Browse for now; Instrument comes later
  office: 'browse',  // Office is not re-laid-out in this batch
  chat: 'focus',
  work: 'run'
};

export function modeForPage(page) {
  return PAGE_MODES[page] || 'browse';
}
