/**
 * 登录态管理模块
 */

var TOKEN_REFRESH_BUFFER_SECONDS = 300; // 提前 5 分钟视为过期，抵消客户端时钟漂移

/**
 * 获取当前 token
 * @returns {string|null}
 */
function getToken() {
  return wx.getStorageSync('token') || null;
}

/**
 * 保存 token、openid 以及签发时间、有效期
 * @param {string} token
 * @param {string} openid
 * @param {number} [expireInSeconds] 有效期（秒），后端 /wechat/login 返回
 */
function setToken(token, openid, expireInSeconds) {
  console.log('[setToken] 保存token - 存在:', !!token, '长度:', token ? token.length : 0, 'expire(s):', expireInSeconds || 0);
  wx.setStorageSync('token', token);
  if (openid) {
    wx.setStorageSync('openid', openid);
  }
  wx.setStorageSync('tokenIssuedAt', Date.now());
  wx.setStorageSync('tokenExpireIn', expireInSeconds || 0);

  var savedToken = wx.getStorageSync('token');
  console.log('[setToken] 验证保存 - 读取到的token存在:', !!savedToken);
}

/**
 * 清除登录态
 */
function clearToken() {
  wx.removeStorageSync('token');
  wx.removeStorageSync('openid');
  wx.removeStorageSync('agentId');
  wx.removeStorageSync('tokenIssuedAt');
  wx.removeStorageSync('tokenExpireIn');
}

/**
 * 检查是否已登录
 * @returns {boolean}
 */
function isLoggedIn() {
  return !!wx.getStorageSync('token');
}

/**
 * 读取本地 token 元信息
 * @returns {{token: string|null, issuedAt: number, expireIn: number}}
 */
function getTokenInfo() {
  return {
    token: wx.getStorageSync('token') || null,
    issuedAt: wx.getStorageSync('tokenIssuedAt') || 0,
    expireIn: wx.getStorageSync('tokenExpireIn') || 0
  };
}

/**
 * 判断 token 是否已经过期或即将过期
 * - 无 token 视为过期
 * - 未记录签发时间/有效期的老 token，也视为过期，触发一次刷新后即可修复
 * @param {number} [bufferSeconds] 提前量，默认 300 秒
 * @returns {boolean}
 */
function isTokenExpiredOrAboutToExpire(bufferSeconds) {
  if (bufferSeconds === undefined || bufferSeconds === null) {
    bufferSeconds = TOKEN_REFRESH_BUFFER_SECONDS;
  }
  var info = getTokenInfo();
  if (!info.token) return true;
  if (!info.issuedAt || !info.expireIn) return true;

  var expiresAt = info.issuedAt + info.expireIn * 1000;
  return Date.now() + bufferSeconds * 1000 >= expiresAt;
}

/**
 * 重新执行静默登录
 * 委托给 app.js 的 silentLogin 方法
 * @returns {Promise<void>}
 */
async function refreshLogin() {
  const app = getApp();
  await app.silentLogin();
}

module.exports = {
  getToken,
  setToken,
  clearToken,
  isLoggedIn,
  refreshLogin,
  getTokenInfo,
  isTokenExpiredOrAboutToExpire
};
