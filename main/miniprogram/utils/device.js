/**
 * 设备管理模块
 * 使用微信 openid 作为设备标识，设备注册与绑定
 */

const { post } = require('./request');

// ============ 设备操作 ============

/**
 * OTA 请求：检查/注册设备
 * @param {string} deviceId - openid 作为设备标识
 * @returns {Promise<Object>} 返回 OTA 响应（含 websocket 信息或未绑定状态）
 */
async function checkOrRegisterDevice(deviceId) {
  const res = await post('/ota/', {
    application: {
      name: 'xiaozhi-miniprogram',
      version: '1.0.0'
    },
    board: {
      type: 'wechat-miniprogram',
      mac: deviceId
    },
    chip_model_name: 'wechat-miniprogram'
  }, {
    'Device-ID': deviceId,
    'Client-ID': 'wechat-miniprogram'
  });
  return res;
}

/**
 * 使用验证码绑定设备
 * @param {string} agentId - Agent ID
 * @param {string} deviceCode - 验证码（从OTA响应获取）
 * @returns {Promise<void>}
 */
async function bindDeviceWithCode(agentId, deviceCode) {
  const res = await post(`/device/bind/${agentId}/${deviceCode}`);
  return res;
}

/**
 * 完整的设备自动绑定流程
 * 1. OTA检查（获取验证码）
 * 2. 使用验证码绑定设备
 * 3. 再次OTA检查（获取WebSocket信息）
 * @param {string} deviceId - openid 作为设备标识
 * @param {string} agentId - Agent ID
 * @returns {Promise<Object>} WebSocket连接信息
 */
async function completeDeviceBinding(deviceId, agentId, deviceCode) {
  // 1. 使用传入的验证码绑定设备
  if (!deviceCode) {
    throw new Error('验证码参数为空');
  }

  console.log('使用验证码绑定设备:', deviceCode);

  await bindDeviceWithCode(agentId, deviceCode);
  console.log('设备绑定成功');

  // 2. 再次OTA检查，获取WebSocket信息
  const finalOtaResponse = await checkOrRegisterDevice(deviceId);

  if (!finalOtaResponse.websocket) {
    throw new Error('绑定后OTA响应缺少WebSocket信息');
  }

  return {
    wsUrl: finalOtaResponse.websocket.url,
    wsToken: finalOtaResponse.websocket.token
  };
}

module.exports = {
  checkOrRegisterDevice,
  bindDeviceWithCode,
  completeDeviceBinding,
};
