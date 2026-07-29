const STORAGE_KEY = 'eggbaby_nfc_claim_intent_v1';
const TTL_MS = 30 * 60 * 1000;
const NFC_CLAIM_PATH = 'pages/nfc-claim/nfc-claim';
const CLAIM_REF_PATTERN = /^[A-Za-z0-9_-]{22}$/;

function isNfcClaimPath(path) {
  if (!path) return false;
  const normalized = path.replace(/^\//, '');
  return normalized === NFC_CLAIM_PATH;
}

function captureNfcClaimIntent(options, now = Date.now()) {
  if (!isNfcClaimPath(options && options.path)) {
    return getPendingNfcClaimIntent(now);
  }
  const query = options.query || {};
  if (String(query.v) !== '1' || !CLAIM_REF_PATTERN.test(query.ref || '')) {
    clearPendingNfcClaimIntent();
    return null;
  }
  const intent = {
    type: 'NFC_CLAIM',
    version: 1,
    claimRef: query.ref,
    capturedAt: now,
    expiresAt: now + TTL_MS
  };
  wx.setStorageSync(STORAGE_KEY, intent);
  return intent;
}

function getPendingNfcClaimIntent(now = Date.now()) {
  try {
    const intent = wx.getStorageSync(STORAGE_KEY);
    if (!intent || intent.expiresAt <= now) {
      clearPendingNfcClaimIntent();
      return null;
    }
    return intent;
  } catch (e) {
    clearPendingNfcClaimIntent();
    return null;
  }
}

function clearPendingNfcClaimIntent() {
  try { wx.removeStorageSync(STORAGE_KEY); } catch (e) { /* ignore */ }
}

module.exports = {
  captureNfcClaimIntent,
  getPendingNfcClaimIntent,
  clearPendingNfcClaimIntent,
  isNfcClaimPath,
  STORAGE_KEY,
  TTL_MS
};
