const API = 'http://localhost:8555';

// ── Token / user storage ──────────────────────────────────────────────────────
const getToken        = () => localStorage.getItem('accessToken');
const getRefreshToken = () => localStorage.getItem('refreshToken');
const getUser         = () => JSON.parse(localStorage.getItem('currentUser') || 'null');

function saveTokens(access, refresh) {
  localStorage.setItem('accessToken', access);
  if (refresh) localStorage.setItem('refreshToken', refresh);
}
function saveUser(user) { localStorage.setItem('currentUser', JSON.stringify(user)); }

// ── Logout ────────────────────────────────────────────────────────────────────
async function doLogout() {
  const rt = getRefreshToken();
  if (rt) {
    fetch(`${API}/api/auth/logout`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({refreshToken: rt})
    }).catch(() => {});
  }
  localStorage.clear();
  window.location.href = 'index.html';
}

// ── Token refresh ─────────────────────────────────────────────────────────────
async function tryRefresh() {
  const rt = getRefreshToken();
  if (!rt) throw new Error('no refresh token');
  const res = await fetch(`${API}/api/auth/refresh`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({refreshToken: rt})
  });
  if (!res.ok) throw new Error('refresh failed');
  const data = await res.json();
  saveTokens(data.accessToken, data.refreshToken);
}

// ── Fetch wrapper with auto-refresh ──────────────────────────────────────────
async function api(path, opts = {}) {
  const makeHeaders = () => {
    const h = {'Content-Type': 'application/json', ...(opts.headers || {})};
    const t = getToken();
    if (t) h['Authorization'] = `Bearer ${t}`;
    return h;
  };
  let res = await fetch(API + path, {...opts, headers: makeHeaders()});
  if (res.status === 401 && getRefreshToken()) {
    try {
      await tryRefresh();
      res = await fetch(API + path, {...opts, headers: makeHeaders()});
    } catch {
      doLogout();
      throw new Error('session expired');
    }
  }
  return res;
}

// ── Auth guard ────────────────────────────────────────────────────────────────
function requireAuth(role) {
  const user = getUser();
  if (!user || !getToken()) { window.location.href = 'index.html'; return null; }
  if (role && user.role !== role) {
    const pages = {CLIENT: 'client.html', LANDLORD: 'landlord.html', ADMIN: 'admin.html'};
    window.location.href = pages[user.role] || 'index.html';
    return null;
  }
  return user;
}

// ── Init sidebar user info ────────────────────────────────────────────────────
function initSidebar() {
  const user = getUser();
  if (!user) return;
  const nameEl = document.getElementById('sidebar-name');
  const avatarEl = document.getElementById('sidebar-avatar');
  if (nameEl) nameEl.textContent = `${user.firstName} ${user.lastName}`;
  if (avatarEl) avatarEl.textContent = (user.firstName || '?')[0].toUpperCase();
}

// ── Section switching ─────────────────────────────────────────────────────────
function showSection(id) {
  document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
  document.getElementById(id)?.classList.add('active');
  document.querySelectorAll('.nav-item').forEach(i => i.classList.remove('active'));
  document.querySelector(`.nav-item[data-section="${id}"]`)?.classList.add('active');
}

// ── Modal helpers ─────────────────────────────────────────────────────────────
function openModal(id)  { document.getElementById(id)?.classList.add('open'); }
function closeModal(id) { document.getElementById(id)?.classList.remove('open'); }
document.addEventListener('click', e => {
  if (e.target.classList.contains('modal-overlay')) e.target.classList.remove('open');
});

// ── Toast ─────────────────────────────────────────────────────────────────────
function toast(msg, type = 'info') {
  const el = document.createElement('div');
  el.className = `toast toast-${type}`;
  el.textContent = msg;
  document.body.appendChild(el);
  requestAnimationFrame(() => el.classList.add('show'));
  setTimeout(() => { el.classList.remove('show'); setTimeout(() => el.remove(), 300); }, 3200);
}

// ── Formatters ────────────────────────────────────────────────────────────────
function fmtDate(d)  { return d ? new Date(d).toLocaleDateString('ru-RU') : '—'; }
function fmtMoney(n) { return new Intl.NumberFormat('ru-RU').format(n || 0) + ' ₽'; }

function fmtStatus(s) {
  const m = {
    PENDING:                 ['warning',   'Ожидает'],
    CONFIRMED:               ['success',   'Подтверждено'],
    CANCELLED_BY_CLIENT:     ['danger',    'Отменено клиентом'],
    CANCELLED_BY_LANDLORD:   ['danger',    'Отменено арендодателем'],
    EXPIRED:                 ['secondary', 'Истёк'],
    COMPLETED:               ['info',      'Завершено'],
  };
  const [cls, label] = m[s] || ['secondary', s];
  return `<span class="badge badge-${cls}">${label}</span>`;
}

function fmtRole(r) {
  return {CLIENT: 'Клиент', LANDLORD: 'Арендодатель', ADMIN: 'Администратор'}[r] || r;
}

// ── Loading / empty states ────────────────────────────────────────────────────
function showLoading(id) {
  const el = document.getElementById(id);
  if (el) el.innerHTML = '<div class="loading"><div class="spinner"></div>Загрузка...</div>';
}
function showEmpty(id, msg = 'Ничего не найдено') {
  const el = document.getElementById(id);
  if (el) el.innerHTML = `<div class="empty-state"><div class="empty-icon">📭</div><p>${msg}</p></div>`;
}

// ── Redirect if already logged in (for index.html) ───────────────────────────
function redirectIfLoggedIn() {
  const user = getUser();
  if (user && getToken()) {
    const pages = {CLIENT: 'client.html', LANDLORD: 'landlord.html', ADMIN: 'admin.html'};
    window.location.href = pages[user.role] || 'index.html';
  }
}
