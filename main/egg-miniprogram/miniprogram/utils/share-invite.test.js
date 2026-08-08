const assert = require('assert');

const shareInvite = require('./share-invite');

const originalWx = global.wx;
const STORAGE_KEY = 'pending_share_invite';
const DAY = 24 * 60 * 60 * 1000;
let storage = Object.create(null);

global.wx = {
  setStorageSync(key, value) { storage[key] = value; },
  getStorageSync(key) { return storage[key]; },
  removeStorageSync(key) { delete storage[key]; }
};

function resetStorage() {
  storage = Object.create(null);
}

function run() {
  assert.deepStrictEqual(
    shareInvite.parseEntryOptions({
      path: 'pages/home/home',
      query: { v: '1', source: 'home_share', inviteCode: 'ABCDE' }
    }),
    { code: 'ABCDE', source: 'home_share', version: 1 },
    'a valid home share should produce a minimal context'
  );

  assert.deepStrictEqual(
    shareInvite.parseEntryOptions({
      path: 'pages/home/home',
      query: { v: '1', source: 'home_share', inviteCode: 'EGG-ABCD' }
    }),
    { code: 'EGG-ABCD', source: 'home_share', version: 1 },
    'a legacy hyphenated invitation code should remain shareable'
  );

  [
    { path: 'pages/welcome/welcome', query: { v: '1', source: 'home_share', inviteCode: 'ABCDE' } },
    { path: 'pages/home/home', query: { v: '2', source: 'home_share', inviteCode: 'ABCDE' } },
    { path: 'pages/home/home', query: { v: '1', source: 'other', inviteCode: 'ABCDE' } },
    { path: 'pages/home/home', query: { v: '1', source: 'home_share', inviteCode: 'ABCD!' } },
    { path: 'pages/home/home', query: { v: '1', source: 'home_share', inviteCode: 'ABCD' } },
    { path: 'pages/home/home', query: { v: '1', source: 'home_share', inviteCode: 23456 } }
  ].forEach((options) => {
    assert.strictEqual(shareInvite.parseEntryOptions(options), null,
      'only the exact home-share contract should be accepted');
  });

  resetStorage();
  const saved = shareInvite.savePending(
    { code: '23456', source: 'home_share', version: 1, ignored: 'do not persist' },
    1000
  );
  assert.deepStrictEqual(saved, { code: '23456', source: 'home_share', version: 1, receivedAt: 1000 });
  assert.deepStrictEqual(storage[STORAGE_KEY], saved,
    'only the allow-listed context fields should be stored');
  assert.strictEqual(shareInvite.savePending({ code: 23456, source: 'home_share', version: 1 }, 1000), null,
    'non-string invite codes are not persisted through implicit coercion');
  assert.deepStrictEqual(shareInvite.getPending(1000 + DAY), saved,
    'a context remains available before its seven-day expiry');

  assert.strictEqual(shareInvite.getPending(1000 + 7 * DAY), null,
    'a context expires at seven days');
  assert.strictEqual(storage[STORAGE_KEY], undefined,
    'expired context is removed from storage');

  shareInvite.savePending({ code: '23456', source: 'home_share', version: 1 }, 2000);
  shareInvite.clearPending();
  assert.strictEqual(shareInvite.getPending(2000), null,
    'clearPending removes an otherwise valid context');

  console.log('share-invite.test.js: ALL PASS');
}

try {
  run();
} finally {
  global.wx = originalWx;
}
