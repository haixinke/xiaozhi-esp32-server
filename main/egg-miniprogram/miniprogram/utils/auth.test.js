const assert = require('assert');

const storage = new Map();
global.wx = {
  getStorageSync(key) { return storage.has(key) ? storage.get(key) : ''; },
  setStorageSync(key, value) { storage.set(key, value); },
  removeStorageSync(key) { storage.delete(key); }
};

const auth = require('./auth');
const now = 1_700_000_000_000;
const login = {
  token: 'test-token', userId: 42, openid: 'test-openid',
  isNewUser: true, hasPhone: false, agentId: null, expire: 43_200
};

assert.throws(() => auth.saveSession({ userId: 42 }, now), /登录响应缺少必要字段/);
assert.strictEqual(storage.size, 0);

auth.saveSession(login, now);
assert.deepStrictEqual(auth.getSession(), { ...login, issuedAt: now });
assert.strictEqual(auth.hasValidSession(now + 1000), true);
assert.strictEqual(auth.isExpiringSoon(now + 43_200_000 - 301_000), false);
assert.strictEqual(auth.isExpiringSoon(now + 43_200_000 - 299_000), true);
assert.strictEqual(auth.isExpired(now + 43_200_000), true);

auth.clearSession();
assert.strictEqual(auth.getSession(), null);
assert.strictEqual(storage.size, 0);
console.log('auth.test.js: ALL PASS');
