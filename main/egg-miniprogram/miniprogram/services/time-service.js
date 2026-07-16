/**
 * time-service 轻量实现
 * 直接使用本地时间，不含服务端时间同步逻辑。
 */
function now() {
  return Date.now();
}

function beijingDateKey(timestamp) {
  const date = new Date((timestamp === undefined ? now() : timestamp) + 8 * 60 * 60 * 1000);
  return date.toISOString().slice(0, 10);
}

function formatBeijingDate(timestamp) {
  const key = beijingDateKey(timestamp);
  const parts = key.split('-').map(Number);
  return parts[0] + '年' + parts[1] + '月' + parts[2] + '日';
}

function sync() {
  return Promise.resolve({ ok: true, now: now() });
}

function isAuthoritative() {
  return true;
}

function requireAuthoritative() {
  return { ok: true, now: now(), authoritative: true };
}

module.exports = { now, beijingDateKey, formatBeijingDate, sync, isAuthoritative, requireAuthoritative };
