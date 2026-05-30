/**
 * 虚拟设备管理模块
 * 包含 SHA-256 纯 JS 实现、虚拟 MAC 生成、设备注册与绑定
 */

const { post } = require('./request');

// ============ 轻量级 SHA-256 纯 JS 实现 ============

const K = [
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
  0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
  0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
  0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
  0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
  0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
  0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
  0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
  0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
];

function rightRotate(value, amount) {
  return (value >>> amount) | (value << (32 - amount));
}

function sha256(message) {
  // 将字符串转为 UTF-8 字节数组
  const msgBytes = [];
  for (let i = 0; i < message.length; i++) {
    const code = message.charCodeAt(i);
    if (code < 0x80) {
      msgBytes.push(code);
    } else if (code < 0x800) {
      msgBytes.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f));
    } else if (code < 0x10000) {
      msgBytes.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f));
    } else {
      msgBytes.push(
        0xf0 | (code >> 18), 0x80 | ((code >> 12) & 0x3f),
        0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f)
      );
    }
  }

  const bitLength = msgBytes.length * 8;

  // 填充
  msgBytes.push(0x80);
  while ((msgBytes.length % 64) !== 56) {
    msgBytes.push(0);
  }

  // 附加长度（64位大端）
  for (let i = 56; i >= 0; i -= 8) {
    msgBytes.push((bitLength >>> i) & 0xff);
  }

  // 初始哈希值
  let h0 = 0x6a09e667, h1 = 0xbb67ae85, h2 = 0x3c6ef372, h3 = 0xa54ff53a;
  let h4 = 0x510e527f, h5 = 0x9b05688c, h6 = 0x1f83d9ab, h7 = 0x5be0cd19;

  // 处理每个 512-bit 块
  for (let offset = 0; offset < msgBytes.length; offset += 64) {
    const w = new Array(64);

    for (let i = 0; i < 16; i++) {
      w[i] = (msgBytes[offset + i * 4] << 24) |
              (msgBytes[offset + i * 4 + 1] << 16) |
              (msgBytes[offset + i * 4 + 2] << 8) |
              (msgBytes[offset + i * 4 + 3]);
    }

    for (let i = 16; i < 64; i++) {
      const s0 = rightRotate(w[i - 15], 7) ^ rightRotate(w[i - 15], 18) ^ (w[i - 15] >>> 3);
      const s1 = rightRotate(w[i - 2], 17) ^ rightRotate(w[i - 2], 19) ^ (w[i - 2] >>> 10);
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) | 0;
    }

    let a = h0, b = h1, c = h2, d = h3;
    let e = h4, f = h5, g = h6, h = h7;

    for (let i = 0; i < 64; i++) {
      const S1 = rightRotate(e, 6) ^ rightRotate(e, 11) ^ rightRotate(e, 25);
      const ch = (e & f) ^ (~e & g);
      const temp1 = (h + S1 + ch + K[i] + w[i]) | 0;
      const S0 = rightRotate(a, 2) ^ rightRotate(a, 13) ^ rightRotate(a, 22);
      const maj = (a & b) ^ (a & c) ^ (b & c);
      const temp2 = (S0 + maj) | 0;

      h = g; g = f; f = e;
      e = (d + temp1) | 0;
      d = c; c = b; b = a;
      a = (temp1 + temp2) | 0;
    }

    h0 = (h0 + a) | 0; h1 = (h1 + b) | 0;
    h2 = (h2 + c) | 0; h3 = (h3 + d) | 0;
    h4 = (h4 + e) | 0; h5 = (h5 + f) | 0;
    h6 = (h6 + g) | 0; h7 = (h7 + h) | 0;
  }

  // 输出十六进制
  function toHex(val) {
    return ('00000000' + (val >>> 0).toString(16)).slice(-8);
  }

  return toHex(h0) + toHex(h1) + toHex(h2) + toHex(h3) +
         toHex(h4) + toHex(h5) + toHex(h6) + toHex(h7);
}

// ============ 虚拟 MAC 生成 ============

/**
 * 基于 openid 确定性生成虚拟 MAC 地址
 * 首字节固定 0x02（本地管理地址标志），后续取 SHA-256 前 5 字节
 * @param {string} openid
 * @returns {string} 格式如 "02:A1:B2:C3:D4:E5"
 */
function generateVirtualMAC(openid) {
  const hash = sha256(openid);
  const bytes = [
    0x02,
    parseInt(hash.substring(0, 2), 16),
    parseInt(hash.substring(2, 4), 16),
    parseInt(hash.substring(4, 6), 16),
    parseInt(hash.substring(6, 8), 16),
    parseInt(hash.substring(8, 10), 16)
  ];
  return bytes.map(b => b.toString(16).toUpperCase().padStart(2, '0')).join(':');
}

// ============ 设备操作 ============

/**
 * OTA 请求：检查/注册设备
 * @param {string} mac - 虚拟 MAC 地址
 * @returns {Promise<Object>} 返回 OTA 响应（含 websocket 信息或未绑定状态）
 */
async function checkOrRegisterDevice(mac) {
  const res = await post('/ota/', {
    application: {
      name: 'xiaozhi-miniprogram',
      version: '1.0.0'
    },
    board: {
      type: 'wechat-miniprogram',
      mac: mac
    },
    chip_model_name: 'wechat-miniprogram'
  }, {
    'Device-ID': mac,
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
 * @param {string} mac - 虚拟MAC地址
 * @param {string} agentId - Agent ID
 * @returns {Promise<Object>} WebSocket连接信息
 */
async function completeDeviceBinding(mac, agentId, deviceCode) {
  // 1. 使用传入的验证码绑定设备
  if (!deviceCode) {
    throw new Error('验证码参数为空');
  }

  console.log('使用验证码绑定设备:', deviceCode);

  await bindDeviceWithCode(agentId, deviceCode);
  console.log('设备绑定成功');

  // 2. 再次OTA检查，获取WebSocket信息
  const finalOtaResponse = await checkOrRegisterDevice(mac);

  if (!finalOtaResponse.websocket) {
    throw new Error('绑定后OTA响应缺少WebSocket信息');
  }

  return {
    wsUrl: finalOtaResponse.websocket.url,
    wsToken: finalOtaResponse.websocket.token
  };
}

/**
 * 创建宠物（Pet Birth）
 * 在设备绑定前调用，为用户创建 AI 宠物
 * @param {string} deviceId - 虚拟 MAC 地址
 * @returns {Promise<Object>} 宠物信息
 */
async function createPet(deviceId) {
  try {
    const res = await post('/pet/birth', {
      deviceId: deviceId
    });
    console.log('宠物创建成功:', res);
    return res;
  } catch (err) {
    console.warn('宠物创建失败（可能已存在）:', err);
    // 不抛出错误，允许流程继续
    return null;
  }
}

/**
 * 获取已保存的虚拟 MAC，若无则生成
 * @param {string} openid
 * @returns {string}
 */
function getOrCreateMAC(openid) {
  let mac = wx.getStorageSync('virtualMAC');
  if (!mac && openid) {
    mac = generateVirtualMAC(openid);
    wx.setStorageSync('virtualMAC', mac);
  }
  return mac;
}

module.exports = {
  sha256,
  generateVirtualMAC,
  checkOrRegisterDevice,
  bindDeviceWithCode,
  completeDeviceBinding,
  getOrCreateMAC,
  createPet
};
