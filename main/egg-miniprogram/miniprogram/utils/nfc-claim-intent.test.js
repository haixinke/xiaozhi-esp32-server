const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');

let store;
global.wx = {
  setStorageSync(key, value) { store[key] = value; },
  getStorageSync(key) { return store[key]; },
  removeStorageSync(key) { delete store[key]; }
};

const {
  captureNfcClaimIntent,
  getPendingNfcClaimIntent,
  clearPendingNfcClaimIntent,
  isNfcClaimPath,
  STORAGE_KEY,
  TTL_MS
} = require('./nfc-claim-intent');

const VALID_REF = 'ABCDEFGHIJ1234567890_-';
const NOW = 1_700_000_000_000;

describe('isNfcClaimPath', () => {
  it('matches exact path without leading slash', () => {
    assert.strictEqual(isNfcClaimPath('pages/nfc-claim/nfc-claim'), true);
  });

  it('matches path with leading slash', () => {
    assert.strictEqual(isNfcClaimPath('/pages/nfc-claim/nfc-claim'), true);
  });

  it('rejects null and empty string', () => {
    assert.strictEqual(isNfcClaimPath(null), false);
    assert.strictEqual(isNfcClaimPath(''), false);
    assert.strictEqual(isNfcClaimPath(undefined), false);
  });

  it('rejects other paths', () => {
    assert.strictEqual(isNfcClaimPath('pages/home/home'), false);
    assert.strictEqual(isNfcClaimPath('pages/nfc-claim/other'), false);
  });
});

describe('captureNfcClaimIntent', () => {
  beforeEach(() => { store = {}; });

  it('captures valid NFC claim options and stores intent', () => {
    const options = { path: 'pages/nfc-claim/nfc-claim', query: { v: '1', ref: VALID_REF } };
    const intent = captureNfcClaimIntent(options, NOW);
    assert.ok(intent);
    assert.strictEqual(intent.type, 'NFC_CLAIM');
    assert.strictEqual(intent.version, 1);
    assert.strictEqual(intent.claimRef, VALID_REF);
    assert.strictEqual(intent.capturedAt, NOW);
    assert.strictEqual(intent.expiresAt, NOW + TTL_MS);
    assert.deepStrictEqual(store[STORAGE_KEY], intent);
  });

  it('returns null and clears intent when query.v is not 1', () => {
    store[STORAGE_KEY] = { type: 'NFC_CLAIM', expiresAt: NOW + TTL_MS };
    const options = { path: 'pages/nfc-claim/nfc-claim', query: { v: '2', ref: VALID_REF } };
    const intent = captureNfcClaimIntent(options, NOW);
    assert.strictEqual(intent, null);
    assert.strictEqual(store[STORAGE_KEY], undefined);
  });

  it('returns null and clears intent when ref is invalid', () => {
    const options = { path: 'pages/nfc-claim/nfc-claim', query: { v: '1', ref: 'short' } };
    const intent = captureNfcClaimIntent(options, NOW);
    assert.strictEqual(intent, null);
  });

  it('returns null and clears intent when query is empty', () => {
    const options = { path: 'pages/nfc-claim/nfc-claim', query: {} };
    const intent = captureNfcClaimIntent(options, NOW);
    assert.strictEqual(intent, null);
  });

  it('returns pending intent when path is not NFC claim', () => {
    const existing = { type: 'NFC_CLAIM', claimRef: VALID_REF, capturedAt: NOW, expiresAt: NOW + TTL_MS };
    store[STORAGE_KEY] = existing;
    const intent = captureNfcClaimIntent({ path: 'pages/home/home' }, NOW);
    assert.deepStrictEqual(intent, existing);
  });

  it('returns null when path is not NFC claim and no pending intent', () => {
    const intent = captureNfcClaimIntent({ path: 'pages/home/home' }, NOW);
    assert.strictEqual(intent, null);
  });

  it('returns null when options is null', () => {
    const intent = captureNfcClaimIntent(null, NOW);
    assert.strictEqual(intent, null);
  });

  it('replaces old intent with new valid capture', () => {
    const oldRef = 'OLDREF1234567890ABCDEF';
    store[STORAGE_KEY] = { type: 'NFC_CLAIM', claimRef: oldRef, capturedAt: NOW - 1000, expiresAt: NOW + TTL_MS - 1000 };
    const newRef = 'NEWREF1234567890ABCDEF';
    const options = { path: 'pages/nfc-claim/nfc-claim', query: { v: '1', ref: newRef } };
    const intent = captureNfcClaimIntent(options, NOW);
    assert.strictEqual(intent.claimRef, newRef);
    assert.strictEqual(intent.capturedAt, NOW);
  });
});

describe('getPendingNfcClaimIntent', () => {
  beforeEach(() => { store = {}; });

  it('returns stored intent when not expired', () => {
    const intent = { type: 'NFC_CLAIM', claimRef: VALID_REF, capturedAt: NOW, expiresAt: NOW + TTL_MS };
    store[STORAGE_KEY] = intent;
    const result = getPendingNfcClaimIntent(NOW);
    assert.deepStrictEqual(result, intent);
  });

  it('returns null and clears when expired', () => {
    const intent = { type: 'NFC_CLAIM', claimRef: VALID_REF, capturedAt: NOW - TTL_MS, expiresAt: NOW };
    store[STORAGE_KEY] = intent;
    const result = getPendingNfcClaimIntent(NOW);
    assert.strictEqual(result, null);
    assert.strictEqual(store[STORAGE_KEY], undefined);
  });

  it('returns null when no intent stored', () => {
    const result = getPendingNfcClaimIntent(NOW);
    assert.strictEqual(result, null);
  });

  it('returns null and clears when getStorageSync throws', () => {
    const origGet = wx.getStorageSync;
    wx.getStorageSync = () => { throw new Error('storage error'); };
    store[STORAGE_KEY] = { type: 'NFC_CLAIM', expiresAt: NOW + TTL_MS };
    const result = getPendingNfcClaimIntent(NOW);
    assert.strictEqual(result, null);
    wx.getStorageSync = origGet;
  });
});

describe('clearPendingNfcClaimIntent', () => {
  beforeEach(() => { store = {}; });

  it('removes stored intent', () => {
    store[STORAGE_KEY] = { type: 'NFC_CLAIM', claimRef: VALID_REF };
    clearPendingNfcClaimIntent();
    assert.strictEqual(store[STORAGE_KEY], undefined);
  });

  it('does not throw when nothing stored', () => {
    assert.doesNotThrow(() => clearPendingNfcClaimIntent());
  });
});
