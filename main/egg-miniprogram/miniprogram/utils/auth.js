const PREFIX = 'eggbaby_auth_';
const KEYS = {
  token: `${PREFIX}token`, userId: `${PREFIX}user_id`, openid: `${PREFIX}openid`,
  isNewUser: `${PREFIX}is_new_user`, hasPhone: `${PREFIX}has_phone`,
  agentId: `${PREFIX}agent_id`, issuedAt: `${PREFIX}issued_at`, expire: `${PREFIX}expire`
};
const REQUIRED = ['token', 'userId', 'openid', 'expire'];

function saveSession(session, issuedAt) {
  if (!session || REQUIRED.some((key) => session[key] === undefined || session[key] === null || session[key] === '')) {
    throw new Error('登录响应缺少必要字段');
  }
  const normalized = {
    token: session.token, userId: session.userId, openid: session.openid,
    isNewUser: !!session.isNewUser, hasPhone: !!session.hasPhone,
    agentId: session.agentId || null, issuedAt: issuedAt || Date.now(), expire: Number(session.expire)
  };
  if (!Number.isFinite(normalized.expire) || normalized.expire <= 0) throw new Error('登录响应缺少必要字段');
  Object.keys(KEYS).forEach((key) => wx.setStorageSync(KEYS[key], normalized[key]));
  return normalized;
}

function getSession() {
  const session = {};
  Object.keys(KEYS).forEach((key) => { session[key] = wx.getStorageSync(KEYS[key]); });
  if (REQUIRED.some((key) => session[key] === undefined || session[key] === null || session[key] === '')) return null;
  return session;
}

function expiresAt(session) { return session.issuedAt + session.expire * 1000; }
function isExpired(now) { const s = getSession(); return !s || (now || Date.now()) >= expiresAt(s); }
function isExpiringSoon(now, bufferSeconds) {
  const s = getSession();
  return !s || (now || Date.now()) + (bufferSeconds === undefined ? 300 : bufferSeconds) * 1000 >= expiresAt(s);
}
function hasValidSession(now) { return !isExpired(now); }
function clearSession() { Object.values(KEYS).forEach((key) => wx.removeStorageSync(key)); }
function markPhoneBound() {
  const session = getSession();
  if (!session) return null;
  return saveSession({ ...session, hasPhone: true }, session.issuedAt);
}

module.exports = { saveSession, getSession, clearSession, markPhoneBound, hasValidSession, isExpired, isExpiringSoon };
