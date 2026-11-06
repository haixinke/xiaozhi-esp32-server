const PREFIX = 'eggbaby_auth_';
const KEYS = {
  token: `${PREFIX}token`, userId: `${PREFIX}user_id`, openid: `${PREFIX}openid`,
  isNewUser: `${PREFIX}is_new_user`, hasPhone: `${PREFIX}has_phone`,
  agentId: `${PREFIX}agent_id`, issuedAt: `${PREFIX}issued_at`, expire: `${PREFIX}expire`
};
const REQUIRED = ['token', 'userId', 'openid', 'expire'];
// 只在当前进程区分绑定/账号变更前后的请求；不用持久化，也不包含授权凭据。
let phoneBindingGeneration = 0;

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
  const previous = getSession();
  if (!previous || String(previous.userId) !== String(normalized.userId) || previous.hasPhone !== normalized.hasPhone) {
    phoneBindingGeneration += 1;
  }
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
function clearSession() {
  phoneBindingGeneration += 1;
  Object.values(KEYS).forEach((key) => wx.removeStorageSync(key));
}
function markPhoneBound() {
  const session = getSession();
  if (!session) return null;
  const boundSession = saveSession({ ...session, hasPhone: true }, session.issuedAt);
  phoneBindingGeneration += 1;
  return boundSession;
}

// 请求发出时捕获归属，防止旧账号或真实绑定之前的迟到响应重新锁住当前账号。
function getPhoneBindingContext() {
  const session = getSession();
  return { userId: session ? String(session.userId) : null, generation: phoneBindingGeneration };
}

function isPhoneBindingContextCurrent(context) {
  const current = getPhoneBindingContext();
  return !!context && context.userId === current.userId && context.generation === current.generation;
}

module.exports = { saveSession, getSession, clearSession, markPhoneBound, hasValidSession, isExpired, isExpiringSoon,
  getPhoneBindingContext, isPhoneBindingContextCurrent };
