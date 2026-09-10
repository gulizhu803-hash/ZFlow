<script setup>
defineProps({ name: String, size: { type: [Number, String], default: 20 } });
const paths = {
  home: 'm3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z',
  grid: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  chat: 'M21 11.5a8.5 8.5 0 0 1-8.5 8.5H4l-2 2v-10A9 9 0 0 1 11 3h1.5a8.5 8.5 0 0 1 8.5 8.5Z M7 9h9 M7 13h6',
  bot: 'M12 3v3 M9 3h6 M5 7h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2Z M7 11v2 M17 11v2 M8 17h8 M1 11v5 M23 11v5',
  book: 'M12 5v16 M12 5C9 3 6 3 3 4v15c3-1 6-1 9 2 3-3 6-3 9-2V4c-3-1-6-1-9 1Z',
  compass: 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z m-6-4-3 5-5 3 3-5Z',
  clock: 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z M12 6v6l4 2',
  settings: 'm10 3-1 3-3 1-3-1-2 4 2 2v3l-1 2 3 3 3-1 3 1 1 2 4-1 1-3 2-2 3-1-1-4-3-1-2-2V3Z M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  search: 'M18 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0Z m-2 6 5 5',
  plus: 'M12 5v14 M5 12h14', close: 'm6 6 12 12 M18 6 6 18', arrow: 'M4 12h16 m-6-6 6 6-6 6', up: 'M12 20V4 m-6 6 6-6 6 6', chevron: 'm9 5 7 7-7 7', down: 'm6 9 6 6 6-6',
  external: 'M14 3h7v7 m0-7-12 12 M10 3H4a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h16a1 1 0 0 0 1-1v-6',
  sparkles: 'm12 3 2.8 6.2L21 12l-6.2 2.8L12 21l-2.8-6.2L3 12l6.2-2.8Z M20 2v4 M18 4h4',
  workflow: 'M3 3h6v6H3z M15 15h6v6h-6z M15 3h6v6h-6z M6 9v6a3 3 0 0 0 3 3h6 M9 6h6',
  repeat: 'm17 2 4 4-4 4 M21 6H7a4 4 0 0 0-4 4 m4 12-4-4 4-4 M3 18h14a4 4 0 0 0 4-4',
  pen: 'm16 3 5 5-13 13H3v-5Z M13 6l5 5', code: 'm8 5-6 7 6 7 m8-14 6 7-6 7 M14 3l-4 18', play: 'm8 4 13 8-13 8Z', stop: 'M5 5h14v14H5z', check: 'm5 12 4 4L19 6',
  checkCircle: 'M22 12a10 10 0 1 1-5-8.7 M8 11l4 4 9-10', file: 'M14 2H5a1 1 0 0 0-1 1v18a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1V8Z M14 2v6h6 M8 13h8 M8 17h6',
  upload: 'M12 16V3 m-5 5 5-5 5 5 M3 15v5a1 1 0 0 0 1 1h16a1 1 0 0 0 1-1v-5',
  link: 'm10 13 4-4 M8 16l-1 1a4 4 0 0 1-6-6l5-5a4 4 0 0 1 6 0 M16 8l1-1a4 4 0 0 1 6 6l-5 5a4 4 0 0 1-6 0',
  heart: 'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z', bookmark: 'M5 3h14v19l-7-4-7 4Z', more: 'M4 12h.01 M12 12h.01 M20 12h.01',
  trash: 'M3 6h18 M9 6V3h6v3 M5 6l1 15h12l1-15 M10 10v7 M14 10v7', copy: 'M9 9h12v12H9z M15 9V3H3v12h6', plug: 'M8 3v5 M16 3v5 M5 8h14v3a7 7 0 0 1-14 0Z M12 18v4',
  globe: 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z M2 12h20 M12 2c6 6 6 14 0 20-6-6-6-14 0-20', layers: 'm12 3 10 5-10 5L2 8Z m0 14 10-5 M2 17l10 5 M2 13l10 5 10-5',
  list: 'M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01', help: 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z M9 8a3 3 0 1 1 5 3c-2 1-2 2-2 3 M12 17h.01',
  logout: 'M9 3H3v18h6 M9 12h12 m-5-5 5 5-5 5', menu: 'M3 6h18 M3 12h18 M3 18h18', refresh: 'M21 3v6h-6 M3 21v-6h6 M4 9a9 9 0 0 1 15-5l2 5 M3 15l2 5a9 9 0 0 0 15-5',
  shield: 'm12 2 9 4v6c0 6-9 10-9 10S3 18 3 12V6Z m-5 10 3 3 7-7', sun: 'M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z M12 1v2 M12 21v2 M1 12h2 M21 12h2 m-2-8-2 2 M5 19l2-2 M5 5l2 2 m12 12-2-2',
  terminal: 'm4 6 6 6-6 6 M12 18h8', alert: 'm12 3 10 18H2Z M12 9v5 M12 17h.01'
};
</script>
<template><svg :width="size" :height="size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="paths[name] || paths.sparkles" /></svg></template>
