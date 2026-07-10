const { API_BASE_URL } = require('../config/api');
const auth = require('./auth');

const MESSAGES = {
  network: '暂时无法连接服务，请稍后重试',
  unauthorized: '登录状态已失效，请重新登录',
  business: '操作失败，请稍后重试',
  invalidResponse: '服务响应异常，请稍后重试'
};

function createError(type, statusCode, code) {
  return { type, statusCode, code, userMessage: MESSAGES[type] };
}

function performRequest(options, retried401) {
  const { url, method = 'GET', data, header = {}, anonymous = false } = options;
  const session = auth.getSession();

  if (!anonymous && (!session || !session.token)) {
    return Promise.reject(createError('unauthorized', 401));
  }

  const requestHeader = anonymous
    ? { ...header }
    : { ...header, Authorization: `Bearer ${session.token}` };

  return new Promise((resolve, reject) => {
    wx.request({
      url: `${API_BASE_URL}${url}`,
      method,
      data,
      header: requestHeader,
      success: async (response) => {
        const statusCode = response && response.statusCode;
        if (statusCode === 401) {
          if (anonymous || retried401) {
            reject(createError('unauthorized', statusCode));
            return;
          }
          auth.clearSession();
          try {
            await getApp().silentLogin();
          } catch (error) {
            reject(createError('unauthorized', statusCode));
            return;
          }
          resolve(performRequest(options, true));
          return;
        }

        if (typeof statusCode !== 'number' || statusCode < 200 || statusCode >= 300) {
          reject(createError('network', statusCode));
          return;
        }

        const envelope = response.data;
        if (!envelope || typeof envelope.code !== 'number') {
          reject(createError('invalidResponse', statusCode));
          return;
        }
        if (envelope.code !== 0) {
          reject(createError('business', statusCode, envelope.code));
          return;
        }
        resolve(envelope.data);
      },
      fail: () => reject(createError('network'))
    });
  });
}

function request(options) {
  return performRequest(options, false);
}

function get(url, data, options = {}) { return request({ ...options, url, data, method: 'GET' }); }
function post(url, data, options = {}) { return request({ ...options, url, data, method: 'POST' }); }
function put(url, data, options = {}) { return request({ ...options, url, data, method: 'PUT' }); }
function del(url, data, options = {}) { return request({ ...options, url, data, method: 'DELETE' }); }

module.exports = { request, get, post, put, del };
