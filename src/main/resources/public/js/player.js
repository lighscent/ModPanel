/* Player detail page (/players/{name}). */

import { ui, escapeHtml, apiFetch, setTitle } from './common.js';

function detailPlayerName() {
  const match = location.pathname.match(/^\/players\/([^/]+)$/);
  return match ? decodeURIComponent(match[1]) : null;
}

function itemSlotImg(type, count) {
  const imgName = type.toLowerCase() + '.png';
  const displayName = type.replace(/_/g, ' ').toLowerCase();
  const countHtml = count > 1 ? '<span class="' + ui.itemCount + '">' + count + '</span>' : '';
  return `<div class="${ui.invSlot}"><img class="${ui.invImg}" src="/items/${imgName}" alt="${displayName}" loading="lazy" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'" /><i class="fas fa-cube text-[13px] text-muted" style="display:none"></i>${countHtml}</div>`;
}

function armorSlotImg(type) {
  const imgName = type.toLowerCase() + '.png';
  const displayName = type.replace(/_/g, ' ').toLowerCase();
  return `<div class="${ui.invSlot}"><img class="${ui.invImg}" src="/items/${imgName}" alt="${displayName}" loading="lazy" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'" /><i class="fas fa-cube text-[13px] text-muted" style="display:none"></i></div>`;
}

function renderPlayerDetail(p) {
  const container = document.getElementById('player-detail');
  const pingClass = p.ping > 150 ? ui.pingBad : p.ping > 80 ? ui.pingWarn : ui.pingGood;
  const itemAt = (slot) => (p.inventory ? p.inventory.find(s => s.slot === slot) : null);

  let invHtml = `<div class="${ui.subSection}">Inventory</div>`;
  for (let row = 0; row < 3; row++) {
    invHtml += `<div class="${ui.invGrid}">`;
    for (let col = 0; col < 9; col++) {
      const item = itemAt(9 + row * 9 + col);
      invHtml += item ? itemSlotImg(item.type, item.amount) : `<div class="${ui.invSlot}"></div>`;
    }
    invHtml += '</div>';
  }
  invHtml += `<div class="${ui.invLabel}">Hotbar</div><div class="${ui.invGrid}">`;
  for (let i = 0; i < 9; i++) {
    const item = itemAt(i);
    invHtml += item ? itemSlotImg(item.type, item.amount) : `<div class="${ui.invSlot}"></div>`;
  }
  invHtml += '</div>';

  let armorHtml = '';
  if (p.armor && p.armor.length > 0) {
    armorHtml += `<div class="${ui.subSection}">Armor</div><div class="mb-2 flex gap-1">`;
    p.armor.forEach(a => { armorHtml += armorSlotImg(a.type); });
    armorHtml += '</div>';
  }

  let offhandHtml = '';
  if (p.offhand) {
    offhandHtml += `<div class="${ui.subSection}">Offhand</div><div class="mb-2 flex gap-1">`;
    offhandHtml += armorSlotImg(p.offhand);
    offhandHtml += '</div>';
  }

  let effectsHtml = '';
  if (p.potionEffects && p.potionEffects.length > 0) {
    effectsHtml += `<div class="${ui.subSection}">Effects</div>`;
    p.potionEffects.forEach(e => {
      effectsHtml += `<span class="${ui.effect}">${escapeHtml(e.type)} ${e.amplifier + 1} (${e.duration}s)</span>`;
    });
  }

  container.innerHTML = `
    <a class="${ui.back}" href="/players"><i class="fas fa-arrow-left"></i> Back to Players</a>
    <div class="${ui.page}"><i class="fas fa-user"></i>${escapeHtml(p.displayName)}</div>
    <div class="mb-6 grid grid-cols-2 gap-3.5 max-[860px]:grid-cols-1">
      <div class="${ui.card} p-4">
        <div class="${ui.detailGrid}">
          <div class="${ui.detailRow}"><span class="${ui.detailLabel}">UUID</span><span class="${ui.detailValue} font-mono text-[11px]">${escapeHtml(p.uuid)}</span></div>
          <div class="${ui.detailRow}"><span class="${ui.detailLabel}">IP</span><span class="${ui.detailValue} font-mono">${escapeHtml(p.ip)}</span></div>
          <div class="${ui.detailRow}"><span class="${ui.detailLabel}">World</span><span class="${ui.detailValue}">${escapeHtml(p.world)}</span></div>
          <div class="${ui.detailRow}"><span class="${ui.detailLabel}">Coords</span><span class="${ui.detailValue} font-mono text-[12px] text-[#7fc4e8]">${Math.round(p.x)}, ${Math.round(p.y)}, ${Math.round(p.z)}</span></div>
          <div class="${ui.detailRow}"><span class="${ui.detailLabel}">Health</span><span class="${ui.detailValue}">${p.health} / ${p.maxHealth}</span></div>
          <div class="${ui.detailRow}"><span class="${ui.detailLabel}">Food</span><span class="${ui.detailValue}">${p.food} / 20</span></div>
          <div class="${ui.detailRow}"><span class="${ui.detailLabel}">Ping</span><span class="${ui.detailValue}"><span class="${ui.ping} ${pingClass}">${p.ping}ms</span></span></div>
          <div class="${ui.detailRow}"><span class="${ui.detailLabel}">Game Mode</span><span class="${ui.detailValue}">${escapeHtml(p.gamemode)}</span></div>
          <div class="${ui.detailRow}"><span class="${ui.detailLabel}">Level</span><span class="${ui.detailValue}">${p.level}</span></div>
          <div class="${ui.detailRow}"><span class="${ui.detailLabel}">Locale</span><span class="${ui.detailValue}">${escapeHtml(p.locale)}</span></div>
        </div>
      </div>
      <div class="${ui.card} p-4">
        ${armorHtml}
        ${offhandHtml}
        ${effectsHtml || '<div class="mt-2 text-[12px] text-dim">No active effects</div>'}
      </div>
    </div>
    ${invHtml}`;
}

function renderPlayerError(name, message) {
  document.getElementById('player-detail').innerHTML = `
    <a class="${ui.back}" href="/players"><i class="fas fa-arrow-left"></i> Back to Players</a>
    <div class="${ui.page}"><i class="fas fa-triangle-exclamation"></i>${name ? 'Player not found' : 'Error'}</div>
    <div class="${ui.empty}">${message}</div>`;
}

async function loadPlayer() {
  const name = detailPlayerName();
  if (!name) {
    renderPlayerError(null, 'No player name in the URL.');
    return;
  }
  setTitle('players', name);
  try {
    const listRes = await apiFetch('/api/players');
    const players = await listRes.json();
    const player = Array.isArray(players)
      ? players.find(p => p.name.toLowerCase() === name.toLowerCase())
      : null;
    if (!player) {
      renderPlayerError(name, `No online player named ${escapeHtml(name)}.`);
      return;
    }
    const res = await apiFetch('/api/players/detail?uuid=' + encodeURIComponent(player.uuid));
    const detail = await res.json();
    if (!detail || !detail.name) throw new Error('Invalid response');
    setTitle('players', detail.displayName || detail.name);
    renderPlayerDetail(detail);
  } catch (e) {
    renderPlayerError(name, 'Failed to load player: ' + escapeHtml(e.message));
  }
}

export async function init() {
  await loadPlayer();
}
