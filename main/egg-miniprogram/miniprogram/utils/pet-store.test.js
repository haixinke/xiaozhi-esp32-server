const assert = require('assert');

const storage = new Map();
global.wx = {
  getStorageSync(key) { return storage.has(key) ? storage.get(key) : ''; },
  setStorageSync(key, value) { storage.set(key, value); },
  removeStorageSync(key) { storage.delete(key); }
};

const petStore = require('./pet-store');

const accountKeys = [
  'eggbaby_mvp_pet_v1',
  'eggbaby_mvp_user_v1',
  'eggbaby_mvp_identity_v1',
  'eggbaby_exhibition_backup_v1'
];

accountKeys.forEach((key) => storage.set(key, { value: key }));
storage.set('eggbaby_theme', 'light');

petStore.clearAccountData();

accountKeys.forEach((key) => assert.strictEqual(storage.has(key), false,
  `account key ${key} should be removed`));
assert.strictEqual(storage.get('eggbaby_theme'), 'light',
  'non-account preference should be preserved');

console.log('pet-store.test.js: ALL PASS');
