/**
 * utils/ota.js
 * --------------------------------------------------------------------------
 * OTA helper for the egg-miniprogram chat channel.
 *
 * Calls POST /xiaozhi/ota/ directly with raw wx.request, because the OTA
 * endpoint returns a raw DeviceReportRespDTO (not the egg request.js
 * {code,msg,data} envelope). Resolves the websocket url + token for the
 * active pet's ai_device.id.
 * --------------------------------------------------------------------------
 */

const { API_BASE_URL } = require('../config/api');

function createError(message) {
  return { type: 'ota', message, userMessage: message };
}

/**
 * Check OTA for the active pet's device and return websocket credentials.
 * @param {string} deviceId - active pet's ai_device.id (macAddress)
 * @returns {Promise<{wsUrl:string, wsToken:string}>}
 */
function checkOrRegisterDevice(deviceId) {
  return new Promise((resolve, reject) => {
    if (!deviceId) {
      reject(createError('缺少宠物设备标识'));
      return;
    }

    const app = getApp();
    const version = (app && app.globalData && app.globalData.version) || '1.0.0-mvp';

    wx.request({
      url: `${API_BASE_URL}/ota/`,
      method: 'POST',
      data: {
        application: {
          name: 'xiaozhi-egg-miniprogram',
          version,
        },
        board: {
          type: 'wechat-egg-miniprogram',
          mac: deviceId,
        },
        chip_model_name: 'wechat-egg-miniprogram',
      },
      header: {
        'Device-ID': deviceId,
        'Client-ID': 'wechat-miniprogram',
      },
      success: (response) => {
        const statusCode = response && response.statusCode;
        const data = response && response.data;

        if (typeof statusCode !== 'number' || statusCode < 200 || statusCode >= 300) {
          reject(createError('获取聊天配置失败：' + statusCode));
          return;
        }

        if (!data || !data.websocket) {
          reject(createError('聊天配置不可用，请确认蛋宝宝已破壳'));
          return;
        }

        resolve({
          wsUrl: data.websocket.url,
          wsToken: data.websocket.token,
        });
      },
      fail: () => {
        reject(createError('网络异常，暂时无法获取聊天配置'));
      },
    });
  });
}

module.exports = { checkOrRegisterDevice };
