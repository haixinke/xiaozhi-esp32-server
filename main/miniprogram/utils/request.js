/**
 * HTTP 请求封装
 * 自动附加 Bearer Token，支持 401 自动重试
 */

// 基础 URL，可按需修改
const BASE_URL = 'http://192.168.170.51:8002/xiaozhi';

/**
 * 发起 HTTP 请求
 * @param {Object} options - 请求参数
 * @param {string} options.url - 相对路径（会拼接 BASE_URL）
 * @param {string} [options.method='GET'] - 请求方法
 * @param {Object} [options.data] - 请求体
 * @param {Object} [options.header] - 额外请求头
 * @param {boolean} [options._isRetry=false] - 内部标记，防止无限重试
 * @returns {Promise<any>}
 */
function request(options) {
  return new Promise((resolve, reject) => {
    const token = wx.getStorageSync('token');
    console.log('[request] URL:', options.url, 'Method:', options.method || 'GET', 'Token exists:', !!token, 'Token value:', token ? token.substring(0, 30) + '...' : 'EMPTY or NULL');

    // 如果需要认证的接口（非登录接口），且没有token，直接拒绝
    const requiresAuth = !options.url.includes('/login') && !options.url.includes('/ota');
    if (requiresAuth && !token) {
      console.error('[request] 需要认证的接口缺少token:', options.url);
      reject({
        statusCode: 401,
        data: { code: 401, msg: '未登录', data: null },
        message: '需要登录'
      });
      return;
    }

    wx.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data,
      timeout: 30000, // 30秒超时
      header: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : '',
        ...options.header
      },
      success: (res) => {
        if (res.statusCode === 401 && !options._isRetry) {
          // Token 过期，重新静默登录后重试
          const app = getApp();
          app.silentLogin().then(() => {
            request({ ...options, _isRetry: true }).then(resolve).catch(reject);
          }).catch(reject);
          return;
        }

        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data);
        } else {
          reject({
            statusCode: res.statusCode,
            data: res.data,
            message: `请求失败: ${res.statusCode}`
          });
        }
      },
      fail: (err) => {
        reject({
          statusCode: -1,
          message: '网络请求失败',
          detail: err
        });
      }
    });
  });
}

/**
 * GET 请求快捷方法
 */
function get(url, data, header) {
  return request({ url, method: 'GET', data, header });
}

/**
 * POST 请求快捷方法
 */
function post(url, data, header) {
  return request({ url, method: 'POST', data, header });
}

/**
 * PUT 请求快捷方法
 */
function put(url, data, header) {
  return request({ url, method: 'PUT', data, header });
}

/**
 * 获取当前 BASE_URL
 */
function getBaseUrl() {
  return BASE_URL;
}

module.exports = {
  request,
  get,
  post,
  put,
  getBaseUrl,
  BASE_URL
};
