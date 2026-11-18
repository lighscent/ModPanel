/* Shared helpers for every panel page: API fetch, live socket, UI class strings. */

export const PAGE_TITLES = {
  dashboard: 'Dashboard',
  players: 'Players',
  users: 'Users',
  whitelist: 'IP Whitelist'
};

export const ui = {
  page: 'mb-5 flex items-center gap-2 text-[19px] font-semibold',
  sectionTitle: 'mb-2.5 mt-6 flex items-center gap-2 text-[15px] font-semibold',
  statsGrid: 'mb-6 grid grid-cols-[repeat(auto-fit,minmax(210px,1fr))] gap-3.5 max-[860px]:grid-cols-[repeat(auto-fit,minmax(150px,1fr))]',
  statCard: 'relative min-h-[96px] overflow-hidden rounded-md px-4 py-3.5 text-white',
  statLabel: 'mb-1 flex items-center gap-1.5 text-[13px] text-white/90',
  statValue: 'stat-value text-[27px] font-bold leading-tight tracking-tight tabular-nums',
  statText: 'stat-value pr-9 text-[17px] font-semibold leading-snug [overflow-wrap:anywhere]',
  statSub: 'mt-2 flex items-center gap-1.5 text-[12.5px] text-white/85',
  ghost: 'pointer-events-none absolute bottom-2 right-2.5 text-[46px] text-white/15',
  teal: 'bg-[linear-gradient(135deg,#1c8296,#12606f)]',
  green: 'bg-[linear-gradient(135deg,#218a49,#16602f)]',
  amber: 'bg-[linear-gradient(135deg,#bd7c1f,#8c5a12)]',
  red: 'bg-[linear-gradient(135deg,#b8433e,#8c2f2b)]',
  blue: 'bg-[linear-gradient(135deg,#1a6fa8,#14517c)]',
  tableWrap: 'overflow-hidden rounded-md border border-line bg-panel max-[860px]:overflow-x-auto',
  table: 'w-full border-collapse max-[860px]:min-w-[520px]',
  th: 'border-b border-line bg-raised px-3.5 py-2 text-left text-[10.5px] font-semibold uppercase tracking-wider text-dim',
  td: 'border-b border-line px-3.5 py-2 text-[13px]',
  row: 'hover:bg-raised/60',
  rowLink: 'cursor-pointer hover:bg-[#36414c]',
  ping: 'inline-block rounded px-1.5 py-0.5 text-[11.5px] font-semibold tabular-nums',
  pingGood: 'bg-emerald-500/15 text-emerald-300',
  pingWarn: 'bg-amber-500/15 text-amber-300',
  pingBad: 'bg-red-500/15 text-red-300',
  empty: 'rounded-md border border-dashed border-[#4a525d] bg-white/[0.02] p-6 text-center text-[13px] text-dim',
  btn: 'inline-flex items-center gap-2 rounded px-3.5 py-1.5 text-[12.5px] font-semibold transition',
  btnPrimary: 'bg-accent text-white hover:bg-accent-dark',
  back: 'mb-3 inline-flex items-center gap-2 rounded border border-[#4a525d] px-2.5 py-1 text-[12px] font-semibold text-dim transition hover:border-accent hover:text-white',
  refresh: 'mt-3 text-right text-[11.5px] text-muted',
  userCard: 'flex items-center justify-between gap-4 rounded-md border border-line bg-panel px-3.5 py-3 hover:border-[#4a525d]',
  badge: 'rounded px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider',
  badgeAdmin: 'bg-red-500/15 text-red-300',
  badgeMod: 'bg-emerald-500/15 text-emerald-300',
  badgeOff: 'bg-white/10 text-dim',
  card: 'rounded-md border border-line bg-panel',
  detailGrid: 'grid grid-cols-2 gap-x-6 max-[860px]:grid-cols-1',
  detailRow: 'flex justify-between gap-4 border-b border-line py-1.5 text-[13px]',
  detailLabel: 'text-dim',
  detailValue: 'text-right font-medium',
  subSection: 'mb-1.5 mt-3.5 text-[11px] font-semibold uppercase tracking-wider text-dim',
  invGrid: 'mb-1 grid grid-cols-9 gap-[3px]',
  invSlot: 'relative flex aspect-square min-h-[40px] max-w-[44px] items-center justify-center rounded-[3px] border border-[#454d58] bg-raised',
  invImg: 'size-[26px] [image-rendering:pixelated]',
  itemCount: 'absolute bottom-0 right-0.5 text-[10px] font-bold text-white [text-shadow:1px_1px_1px_rgba(0,0,0,.85)]',
  invLabel: 'mb-1 mt-2 text-[9.5px] uppercase tracking-wider text-muted',
  effect: 'mr-1 mt-1 inline-block rounded border border-sky-400/30 bg-sky-400/15 px-1.5 py-0.5 text-[10.5px] text-sky-300',
  muted: 'text-[12px] text-dim'
};

export function escapeHtml(str) {
  if (!str) return '';
  return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

export function setTitle(page, detail) {
  const label = PAGE_TITLES[page] || 'Dashboard';
  document.title = detail ? `ModPanel - ${detail} | ${label}` : `ModPanel - ${label}`;
}

export async function apiFetch(url, options = {}) {
  const res = await fetch(url, { ...options, headers: { 'Content-Type': 'application/json', ...options.headers } });
  if (res.status === 401) { window.location.href = '/login'; }
  if (!res.ok && res.headers.get('content-type')?.includes('json')) {
    const err = await res.json();
    throw new Error(err.error || 'Request failed');
  }
  if (!res.ok) throw new Error('Request failed');
  return res;
}

export async function logout() {
  try { await fetch('/api/auth/logout', { method: 'POST' }); } catch (e) {}
  window.location.href = '/login';
}

export function playerUrl(name) {
  return '/players/' + encodeURIComponent(name);
}

/* Inline handlers in the pages (logout button) need a global. */
window.logout = logout;

/* Who am I (cookie session); any failure sends back to the login page. */
let sessionUsername = null;

export async function loadSessionUser() {
  try {
    const r = await fetch('/api/auth/session');
    if (!r.ok) { window.location.href = '/login'; return; }
    const me = await r.json();
    sessionUsername = me.username || 'Unknown';
    applySessionUser();
  } catch (e) {
    window.location.href = '/login';
  }
}

/** Re-applies the cached username after an AJAX body swap. */
export function applySessionUser() {
  const el = document.getElementById('usernameDisplay');
  if (el && sessionUsername) el.textContent = sessionUsername;
}

/* ========== LIVE (WebSocket) ========== */
let liveSocket = null;
let liveConnected = false;
let liveRetryMs = 1000;
let liveHandler = null;

export function liveBadgeHtml() {
  const cls = liveConnected ? 'bg-emerald-500/15 text-emerald-300' : 'bg-white/10 text-dim';
  const label = liveConnected ? 'Live' : 'Reconnecting…';
  return `<span id="liveBadge" class="${ui.badge} ml-auto ${cls}">● ${label}</span>`;
}

export function refreshLiveBadge() {
  const el = document.getElementById('liveBadge');
  if (el) el.outerHTML = liveBadgeHtml();
}

/** handler(players, maxPlayers) or null to stop delivering updates. */
export function initLive(handler) {
  liveHandler = handler;
  if (handler) connectLive();
}

function connectLive() {
  if (liveSocket && (liveSocket.readyState === WebSocket.OPEN || liveSocket.readyState === WebSocket.CONNECTING)) return;
  let ws;
  try {
    const proto = location.protocol === 'https:' ? 'wss://' : 'ws://';
    ws = new WebSocket(proto + location.host + '/api/live');
  } catch (e) {
    scheduleLiveReconnect();
    return;
  }
  liveSocket = ws;
  ws.onopen = () => { liveConnected = true; liveRetryMs = 1000; refreshLiveBadge(); };
  ws.onmessage = (event) => {
    let msg;
    try { msg = JSON.parse(event.data); } catch (e) { return; }
    if (!msg || msg.type !== 'players' || !Array.isArray(msg.players)) return;
    if (liveHandler) liveHandler(msg.players, msg.maxPlayers);
  };
  ws.onclose = () => {
    if (liveSocket !== ws) return;
    liveSocket = null;
    liveConnected = false;
    refreshLiveBadge();
    scheduleLiveReconnect();
  };
  ws.onerror = () => { try { ws.close(); } catch (ignored) {} };
}

function scheduleLiveReconnect() {
  const delay = liveRetryMs;
  liveRetryMs = Math.min(liveRetryMs * 2, 15000);
  setTimeout(() => { liveSocket = null; connectLive(); }, delay);
}
