/**
 * utils/logger.js
 * --------------------------------------------------------------------------
 * 小程序日志工具。
 *   - 开发环境下输出到控制台。
 *   - 生产环境（wx.getAccountInfoSync 的 envVersion 为 release）下静默。
 * --------------------------------------------------------------------------
 */

function _isProduction() {
  try {
    const info = wx.getAccountInfoSync && wx.getAccountInfoSync();
    return !!(info && info.miniProgram && info.miniProgram.envVersion === 'release');
  } catch (_) {
    return false;
  }
}

const isProduction = _isProduction();

function log(level, args) {
  if (isProduction) return;
  if (typeof console[level] === 'function') {
    console[level].apply(console, args);
  }
}

module.exports = {
  debug(...args) { log('debug', args); },
  log(...args) { log('log', args); },
  info(...args) { log('info', args); },
  warn(...args) { log('warn', args); },
  error(...args) { log('error', args); },
};
