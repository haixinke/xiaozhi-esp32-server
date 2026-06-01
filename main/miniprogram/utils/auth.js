/**
 * 登录态管理模块
 */

/**
 * 获取当前 token
 * @returns {string|null}
 */
function getToken() {
  return wx.getStorageSync('token') || null;
}

/**
 * 保存 token 和 openid
 * @param {string} token
 * @param {string} openid
 */
function setToken(token, openid) {
  console.log('[setToken] 保存token - token存在:', !!token, 'token长度:', token ? token.length : 0, 'token前缀:', token ? token.substring(0, 20) : 'N/A');
  wx.setStorageSync('token', token);
  if (openid) {
    wx.setStorageSync('openid', openid);
  }
  // 验证保存是否成功
  const savedToken = wx.getStorageSync('token');
  console.log('[setToken] 验证保存 - 读取到的token存在:', !!savedToken, '读取到的token前缀:', savedToken ? savedToken.substring(0, 20) : 'N/A');
}

/**
 * 清除登录态
 */
function clearToken() {
  wx.removeStorageSync('token');
  wx.removeStorageSync('openid');
  wx.removeStorageSync('agentId');
}

/**
 * 检查是否已登录
 * @returns {boolean}
 */
function isLoggedIn() {
  return !!wx.getStorageSync('token');
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
  refreshLogin
};
