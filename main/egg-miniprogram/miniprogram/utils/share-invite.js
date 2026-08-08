const STORAGE_KEY = 'pending_share_invite';
const TTL = 7 * 24 * 60 * 60 * 1000;
const INVITE_CODE_PATTERN = /^[A-Z0-9-]{5,32}$/;

function parseEntryOptions(options) {
  const query = options && options.query;
  const path = options && options.path;
  if ((path !== '/pages/home/home' && path !== 'pages/home/home')
    || !query
    || String(query.v) !== '1'
    || query.source !== 'home_share'
    || typeof query.inviteCode !== 'string'
    || !INVITE_CODE_PATTERN.test(query.inviteCode)) {
    return null;
  }
  return { code: query.inviteCode, source: 'home_share', version: 1 };
}

function savePending(context, now) {
  if (!isValidContext(context)) return null;
  const pending = {
    code: context.code,
    source: context.source,
    version: context.version,
    receivedAt: now === undefined ? Date.now() : now
  };
  if (!Number.isFinite(pending.receivedAt)) return null;
  wx.setStorageSync(STORAGE_KEY, pending);
  return pending;
}

function getPending(now) {
  const pending = wx.getStorageSync(STORAGE_KEY);
  if (!isValidStoredContext(pending)) {
    clearPending();
    return null;
  }
  const currentTime = now === undefined ? Date.now() : now;
  if (!Number.isFinite(currentTime) || currentTime - pending.receivedAt >= TTL) {
    clearPending();
    return null;
  }
  return {
    code: pending.code,
    source: pending.source,
    version: pending.version,
    receivedAt: pending.receivedAt
  };
}

function clearPending() {
  wx.removeStorageSync(STORAGE_KEY);
}

function isValidContext(context) {
  return !!context
    && typeof context.code === 'string'
    && INVITE_CODE_PATTERN.test(context.code)
    && context.source === 'home_share'
    && context.version === 1;
}

function isValidStoredContext(context) {
  return isValidContext(context)
    && Number.isFinite(context.receivedAt)
    && context.receivedAt >= 0;
}

module.exports = { parseEntryOptions, savePending, getPending, clearPending };
