const { get, post } = require('./request');

function preview(claimRef) {
  return get('/pdc/nfc/claim/preview', { claimRef });
}

function confirm(claimRef, requestId) {
  return post('/pdc/nfc/claim/confirm', { claimRef, requestId });
}

module.exports = { preview, confirm };
