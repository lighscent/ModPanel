/* AJAX navigation: swaps <body> content, runs the matching page module. */

import { loadSessionUser, applySessionUser, initLive } from './common.js';

const ROUTES = [
  { test: (p) => p === '/dashboard', module: '/js/dashboard.js' },
  { test: (p) => p === '/players', module: '/js/players.js' },
  { test: (p) => p === '/users', module: '/js/users.js' },
  { test: (p) => p === '/whitelist', module: '/js/whitelist.js' },
  { test: (p) => /^\/players\/[^/]+$/.test(p), module: '/js/player.js' }
];

let currentCleanup = null;
let mountedPath = null;

function routeFor(path) {
  const route = ROUTES.find((r) => r.test(path));
  return route ? route.module : null;
}

/** Unmount the current page (timers, live handler). */
function unmount() {
  if (currentCleanup) {
    try { currentCleanup(); } catch (e) { /* ignore */ }
    currentCleanup = null;
  }
  initLive(null);
}

async function mount(path) {
  const modulePath = routeFor(path);
  if (!modulePath) return;
  try {
    const mod = await import(modulePath);
    mountedPath = path;
    currentCleanup = (await mod.init()) || null;
  } catch (e) {
    console.error('Failed to mount ' + path, e);
  }
}

export async function navigate(path, push = true) {
  if (path === mountedPath && push) return;
  unmount();
  let doc;
  try {
    const res = await fetch(path, { headers: { 'X-Requested-With': 'ajax' } });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    doc = new DOMParser().parseFromString(await res.text(), 'text/html');
  } catch (e) {
    window.location.href = path;
    return;
  }
  const sourceBody = doc.body;
  const sourceMain = doc.querySelector('main');
  if (!sourceBody || !sourceMain) {
    // Redirection (login) ou page inattendue : rechargement classique.
    window.location.href = path;
    return;
  }

  if (push) history.pushState({ path }, '', path);
  document.title = doc.title;
  document.body.innerHTML = sourceBody.innerHTML;
  document.body.className = sourceBody.className;
  applySessionUser();
  window.scrollTo(0, 0);
  await mount(path);
}

/* Internal links -> AJAX. */
document.addEventListener('click', (event) => {
  if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
  const link = event.target.closest('a[href]');
  if (!link || link.target || link.hasAttribute('download')) return;
  const href = link.getAttribute('href');
  if (!href.startsWith('/') || href.startsWith('/api/') || href.startsWith('//')) return;
  if (!routeFor(href.split('?')[0])) return;
  event.preventDefault();
  navigate(href.split('?')[0]);
});

/* Player rows -> AJAX. */
document.addEventListener('click', (event) => {
  const row = event.target.closest('[data-player]');
  if (!row) return;
  const name = row.getAttribute('data-player');
  if (name) navigate('/players/' + encodeURIComponent(name));
});

window.addEventListener('popstate', () => {
  navigate(location.pathname, false);
});

/* Boot: page loaded normally, mount its module. */
window.addEventListener('DOMContentLoaded', async () => {
  await loadSessionUser();
  await mount(location.pathname);
});
