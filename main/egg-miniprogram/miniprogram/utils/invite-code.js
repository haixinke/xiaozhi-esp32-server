const QR_PREFIX = 'iv1-';

function isInviteCode(code) {
  return typeof code === 'string' && code.length >= 5 && code.length <= 32 && !/[^A-Z0-9-]/.test(code);
}

function parseInviteQr(value) {
  if (typeof value !== 'string' || !value.startsWith(QR_PREFIX)) return null;
  const code = value.slice(QR_PREFIX.length);
  return isInviteCode(code) ? code : null;
}

module.exports = { isInviteCode, parseInviteQr };
