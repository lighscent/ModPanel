/* Players page: full online-player table, live + adaptive polling. */

import { ui, escapeHtml, apiFetch, initLive, refreshLiveBadge } from './common.js';

let playersTimer = null;
let lastPlayerCount = 0;

function playersRefreshDelay(count) {
  if (count <= 5) return 2000;
  if (count <= 15) return 3000;
  if (count <= 40) return 4000;
  return 5000;
}

function renderPlayers(players) {
  lastPlayerCount = players.length;
  const target = document.getElementById('players-table');
  if (!target) return;
  if (players.length === 0) {
    target.innerHTML = `<div class="${ui.empty}">No players online</div>`;
    return;
  }
  target.innerHTML = `<div class="${ui.tableWrap}"><table class="${ui.table}"><thead><tr>
    <th class="${ui.th}">Player</th><th class="${ui.th}">World</th><th class="${ui.th}">Health</th><th class="${ui.th}">Ping</th><th class="${ui.th}">IP</th><th class="${ui.th}">Coords</th><th class="${ui.th}">Game Mode</th>
  </tr></thead><tbody>
    ${players.map(p => `<tr class="${ui.rowLink}" data-player="${escapeHtml(p.name)}">
      <td class="${ui.td}"><span class="font-semibold text-[#7fc4e8]">${escapeHtml(p.displayName)}</span></td>
      <td class="${ui.td} text-dim">${escapeHtml(p.world)}</td>
      <td class="${ui.td}">${p.health}/${p.maxHealth}</td>
      <td class="${ui.td}"><span class="${ui.ping} ${p.ping > 150 ? ui.pingBad : p.ping > 80 ? ui.pingWarn : ui.pingGood}">${p.ping}ms</span></td>
      <td class="${ui.td} font-mono text-[11.5px] text-dim">${escapeHtml(p.ip)}</td>
      <td class="${ui.td} font-mono text-[12px] text-[#7fc4e8]">${p.x}, ${p.y}, ${p.z}</td>
      <td class="${ui.td} text-dim">${escapeHtml(p.gamemode)}</td>
    </tr>`).join('')}
  </tbody></table></div>`;
}

async function refreshPlayers() {
  playersTimer = null;
  try {
    const res = await apiFetch('/api/players');
    const players = await res.json();
    if (Array.isArray(players)) renderPlayers(players);
  } catch (e) { /* prochaine tentative */ }
  schedulePlayersRefresh();
}

function schedulePlayersRefresh() {
  if (playersTimer) clearTimeout(playersTimer);
  playersTimer = setTimeout(refreshPlayers, playersRefreshDelay(lastPlayerCount));
}

export async function init() {
  refreshPlayers();
  initLive((players) => {
    renderPlayers(players);
    schedulePlayersRefresh();
  });
  refreshLiveBadge();

  return () => {
    if (playersTimer) clearTimeout(playersTimer);
  };
}
