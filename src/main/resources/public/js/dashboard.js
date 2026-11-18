/* Dashboard page: stats tiles + compact online-player list. */

import { ui, escapeHtml, apiFetch, initLive } from './common.js';

let statsTimer = null;
let playersTimer = null;
let lastPlayerCount = 0;

function playersRefreshDelay(count) {
  if (count <= 5) return 2000;
  if (count <= 15) return 3000;
  if (count <= 40) return 4000;
  return 5000;
}

function updatePlayerCountCard(count, max) {
  const cards = document.querySelectorAll('.stat-value');
  if (cards[0] && typeof max !== 'undefined') {
    cards[0].innerHTML = count + '<span class="text-[16px] font-medium text-white/70">/' + max + '</span>';
  }
}

function renderStats(d) {
  const tpsClass = d.tps < 15 ? ui.red : d.tps < 18 ? ui.amber : ui.green;
  const rawVersion = d.mcVersion || '';
  const mcStart = rawVersion.indexOf('(MC:');
  const mcEnd = mcStart < 0 ? -1 : rawVersion.indexOf(')', mcStart + 4);
  const mcVersion = mcEnd > mcStart + 4 ? rawVersion.slice(mcStart + 4, mcEnd).trim() : rawVersion;
  const implVersion = (mcStart < 0 ? rawVersion : rawVersion.slice(0, mcStart)).trim();

  const stats = document.getElementById('stats');
  if (!stats) return;
  stats.innerHTML = `
    <div class="${ui.statsGrid}">
      <div class="${ui.statCard} ${ui.teal}">
        <i class="fas fa-user-group ${ui.ghost}"></i>
        <div class="${ui.statLabel}"><i class="fas fa-user-group"></i>Players Online</div>
        <div class="${ui.statValue}">${d.playerCount}<span class="text-[16px] font-medium text-white/70">/${d.maxPlayers}</span></div>
        <div class="${ui.statSub}">${escapeHtml(d.serverName || 'Minecraft Server')}</div>
      </div>
      <div class="${ui.statCard} ${tpsClass}">
        <i class="fas fa-gauge-high ${ui.ghost}"></i>
        <div class="${ui.statLabel}"><i class="fas fa-gauge-high"></i>TPS</div>
        <div class="${ui.statValue}">${d.tps.toFixed(1)}</div>
        <div class="${ui.statSub}">Target: 20.0</div>
      </div>
      <div class="${ui.statCard} ${ui.blue}">
        <i class="fas fa-server ${ui.ghost}"></i>
        <div class="${ui.statLabel}"><i class="fas fa-server"></i>Server</div>
        <div class="${ui.statText}" title="${escapeHtml(implVersion)}">${escapeHtml((d.serverName || 'Server') + ': ' + (mcVersion || 'N/A'))}</div>
        <div class="${ui.statSub}">Version</div>
      </div>
    </div>`;
}

async function refreshStats() {
  try {
    const res = await apiFetch('/api/stats');
    const d = await res.json();
    if (d && typeof d.playerCount !== 'undefined') renderStats(d);
  } catch (e) { /* garde l'affichage précédent */ }
}

function renderPlayersMini(players) {
  lastPlayerCount = players.length;
  const list = document.getElementById('dashboard-players-list');
  if (!list) return;
  if (players.length === 0) {
    list.innerHTML = `<div class="${ui.empty}"><i class="fas fa-user-slash mb-1.5 block text-[18px] opacity-50"></i>No players online</div>`;
    const info = document.getElementById('refreshInfo');
    if (info) info.textContent = '';
    return;
  }
  list.innerHTML = `<div class="${ui.tableWrap}"><table class="${ui.table}"><thead><tr>
    <th class="${ui.th}">Player</th><th class="${ui.th}">World</th><th class="${ui.th}">Health</th><th class="${ui.th}">Ping</th>
  </tr></thead><tbody>
    ${players.map(p => `<tr class="${ui.rowLink}" data-player="${escapeHtml(p.name)}">
      <td class="${ui.td}"><span class="font-semibold text-[#7fc4e8]">${escapeHtml(p.displayName)}</span></td>
      <td class="${ui.td} text-dim">${escapeHtml(p.world)}</td>
      <td class="${ui.td}">${p.health}/${p.maxHealth}</td>
      <td class="${ui.td}"><span class="${ui.ping} ${p.ping > 150 ? ui.pingBad : p.ping > 80 ? ui.pingWarn : ui.pingGood}">${p.ping}ms</span></td>
    </tr>`).join('')}
  </tbody></table></div>`;
  const info = document.getElementById('refreshInfo');
  if (info) info.textContent = 'Last updated: ' + new Date().toLocaleTimeString();
}

async function refreshPlayers() {
  playersTimer = null;
  try {
    const res = await apiFetch('/api/players');
    const players = await res.json();
    if (Array.isArray(players)) renderPlayersMini(players);
  } catch (e) { /* prochaine tentative */ }
  schedulePlayersRefresh();
}

function schedulePlayersRefresh() {
  if (playersTimer) clearTimeout(playersTimer);
  playersTimer = setTimeout(refreshPlayers, playersRefreshDelay(lastPlayerCount));
}

export async function init() {
  refreshStats();
  refreshPlayers();
  statsTimer = setInterval(refreshStats, 5000);
  initLive((players, max) => {
    renderPlayersMini(players);
    updatePlayerCountCard(players.length, max);
    schedulePlayersRefresh();
  });

  return () => {
    if (statsTimer) clearInterval(statsTimer);
    if (playersTimer) clearTimeout(playersTimer);
  };
}
