/* Users page: panel accounts + create-user modal. */

import { ui, escapeHtml, apiFetch } from './common.js';

async function loadUsers() {
  const target = document.getElementById('users-list');
  if (!target) return;
  try {
    const res = await apiFetch('/api/users');
    const users = await res.json();
    if (!Array.isArray(users)) throw new Error('Invalid users data');
    if (users.length === 0) {
      target.innerHTML = `<div class="${ui.empty}">No users yet</div>`;
      return;
    }
    target.innerHTML = `<div class="grid gap-1.5">
      ${users.map(u => `<div class="${ui.userCard}">
        <div class="flex flex-col gap-0.5">
          <div class="text-[13.5px] font-semibold">${escapeHtml(u.username)}</div>
          <div class="text-[11.5px] text-dim">Role: ${escapeHtml(u.role)} | Created: ${u.createdAt ? escapeHtml(u.createdAt.split('T')[0]) : 'N/A'} | Last login: ${u.lastLogin ? escapeHtml(u.lastLogin.split('T')[0]) : 'Never'}</div>
        </div>
        <div class="flex flex-wrap gap-1.5">
          ${u.role === 'admin' || u.role === 'owner' ? `<span class="${ui.badge} ${ui.badgeAdmin}">admin</span>` : `<span class="${ui.badge} ${ui.badgeMod}">mod</span>`}
          ${!u.enabled ? `<span class="${ui.badge} ${ui.badgeOff}">disabled</span>` : ''}
        </div>
      </div>`).join('')}
    </div>`;
  } catch (e) {
    target.innerHTML = `<div class="${ui.empty}">Error loading users</div>`;
  }
}

function openCreateUser() {
  const modal = document.getElementById('createUserModal');
  if (!modal) return;
  modal.classList.remove('hidden');
  modal.classList.add('flex');
}

function closeCreateUser() {
  const modal = document.getElementById('createUserModal');
  if (!modal) return;
  modal.classList.add('hidden');
  modal.classList.remove('flex');
}

async function createUser() {
  const username = document.getElementById('newUsername').value;
  const password = document.getElementById('newPassword').value;
  const role = document.getElementById('newRole').value;
  if (!username || !password) return alert('Fill all fields');
  try {
    await apiFetch('/api/users/create', {
      method: 'POST',
      body: JSON.stringify({ username, password, role })
    });
    closeCreateUser();
    document.getElementById('newUsername').value = '';
    document.getElementById('newPassword').value = '';
    loadUsers();
  } catch (e) {
    alert(e.message || 'Failed to create user');
  }
}

/* Inline handlers in users.html need globals. */
window.openCreateUser = openCreateUser;
window.closeCreateUser = closeCreateUser;
window.createUser = createUser;

export async function init() {
  loadUsers();
}
