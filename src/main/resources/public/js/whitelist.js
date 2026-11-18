/* IP whitelist page. */

import { ui, escapeHtml, apiFetch } from './common.js';

async function loadWhitelist() {
  const target = document.getElementById('whitelist-table');
  if (!target) return;
  try {
    const res = await apiFetch('/api/whitelist');
    let entries = await res.json();
    if (!Array.isArray(entries)) entries = [];
    target.innerHTML = `<div class="${ui.tableWrap}">
      <table class="${ui.table}">
        <thead><tr><th class="${ui.th}">IP Address</th><th class="${ui.th}">Added At</th></tr></thead>
        <tbody>
          ${entries.length === 0 ? `<tr><td colspan="2" class="${ui.td} text-center text-dim"><i class="fas fa-shield-halved"></i> No entries</td></tr>` :
            entries.map(e => `<tr class="${ui.row}"><td class="${ui.td} font-mono">${escapeHtml(e.ip)}</td><td class="${ui.td} text-dim">${escapeHtml(e.addedAt || 'N/A')}</td></tr>`).join('')}
        </tbody>
      </table>
    </div>`;
  } catch (e) {
    target.innerHTML = `<div class="${ui.empty}">Error loading whitelist</div>`;
  }
}

export async function init() {
  loadWhitelist();
}
