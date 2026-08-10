const assert = require('assert');
const Module = require('module');

// ---- 测试桩：global.wx 用 Map 存储，通过 Module._load 注入 mock ----
const storage = new Map();
global.wx = {
  getStorageSync(key) { return storage.has(key) ? storage.get(key) : ''; },
  setStorageSync(key, value) { storage.set(key, value); },
  removeStorageSync(key) { storage.delete(key); },
  uploadFile: null
};

const uploads = [];
const requests = { gets: [], posts: [] };
const API_BASE_URL = 'https://api.example/xiaozhi';

const originalLoad = Module._load;
Module._load = function (req) {
  if (req === '../config/api') return { API_BASE_URL, OSS_SCENE_BASE: 'https://oss.example/scenes' };
  if (req === './auth') {
    return {
      getSession: () => ({ token: 'token-42', userId: 42, openid: 'openid-42' }),
      clearSession: () => {}
    };
  }
  if (req === './request') {
    return {
      get: async (url) => {
        requests.gets.push(url);
        const response = requestResponses.shift();
        if (response instanceof Error) throw response;
        return response;
      },
      post: async (url, data) => {
        requests.posts.push({ url, data });
        const response = requestResponses.shift();
        if (response instanceof Error) throw response;
        return response;
      }
    };
  }
  return originalLoad.apply(this, arguments);
};

let requestResponses = [];

const doodleApi = require('./doodle-api');

function reset() {
  uploads.length = 0;
  requests.gets.length = 0;
  requests.posts.length = 0;
  requestResponses = [];
}

function mockUploadFile(options) {
  uploads.push(options);
  const response = uploadResponses.shift();
  if (response && response.fail) {
    if (options.fail) options.fail(response.fail);
    return;
  }
  if (options.success) options.success(response || {});
}

let uploadResponses = [];

(async () => {
  // ===== uploadDoodleImage =====

  // 1. 成功：解析 envelope.data 为 URL
  reset();
  wx.uploadFile = mockUploadFile;
  uploadResponses = [{ statusCode: 200, data: JSON.stringify({ code: 0, data: 'https://oss.example/doodles/1.png' }) }];
  const uploaded = await doodleApi.uploadDoodleImage('/tmp/doodle.png');
  assert.strictEqual(uploaded, 'https://oss.example/doodles/1.png', 'uploadDoodleImage resolves envelope.data');
  assert.strictEqual(uploads.length, 1, 'uploadDoodleImage calls wx.uploadFile once');
  assert.strictEqual(uploads[0].url, `${API_BASE_URL}/wechat/avatar`, 'uploadDoodleImage uses /wechat/avatar');
  assert.strictEqual(uploads[0].filePath, '/tmp/doodle.png', 'uploadDoodleImage passes tempFilePath');
  assert.strictEqual(uploads[0].name, 'file', 'uploadDoodleImage field name is file');
  assert.strictEqual(uploads[0].header.Authorization, 'Bearer token-42', 'uploadDoodleImage adds Bearer header');

  // 2. statusCode 500 → reject '画作上传失败'
  reset();
  uploadResponses = [{ statusCode: 500, data: '' }];
  try {
    await doodleApi.uploadDoodleImage('/tmp/doodle.png');
    assert.fail('should reject on statusCode 500');
  } catch (error) {
    assert.strictEqual(error.userMessage, '画作上传失败', 'statusCode 500 surfaces upload error');
  }

  // 3. envelope.code !== 0 → reject envelope.msg
  reset();
  uploadResponses = [{ statusCode: 200, data: JSON.stringify({ code: 10001, msg: '图片格式不支持' }) }];
  try {
    await doodleApi.uploadDoodleImage('/tmp/doodle.png');
    assert.fail('should reject on business error');
  } catch (error) {
    assert.strictEqual(error.userMessage, '图片格式不支持', 'business error surfaces server msg');
  }

  // 4. envelope.data 缺失 → reject fallback
  reset();
  uploadResponses = [{ statusCode: 200, data: JSON.stringify({ code: 0 }) }];
  try {
    await doodleApi.uploadDoodleImage('/tmp/doodle.png');
    assert.fail('should reject when data missing');
  } catch (error) {
    assert.strictEqual(error.userMessage, '画作上传失败', 'missing data falls back');
  }

  // 5. JSON 解析失败 → reject fallback
  reset();
  uploadResponses = [{ statusCode: 200, data: 'not-json' }];
  try {
    await doodleApi.uploadDoodleImage('/tmp/doodle.png');
    assert.fail('should reject on invalid JSON');
  } catch (error) {
    assert.strictEqual(error.userMessage, '画作上传失败', 'invalid JSON falls back');
  }

  // 6. wx.uploadFile fail → reject fallback
  reset();
  uploadResponses = [{ fail: { errMsg: 'network' } }];
  try {
    await doodleApi.uploadDoodleImage('/tmp/doodle.png');
    assert.fail('should reject on upload fail');
  } catch (error) {
    assert.strictEqual(error.userMessage, '画作上传失败', 'upload fail falls back');
  }

  // 7. 无登录态 → reject '登录状态已失效'
  reset();
  let hasSession = false;
  const doodleApiNoSession = require('./doodle-api');
  Module._load = function (req) {
    if (req === './auth') return { getSession: () => (hasSession ? { token: 't' } : null), clearSession: () => {} };
    return originalLoad.apply(this, arguments);
  };
  delete require.cache[require.resolve('./doodle-api')];
  const doodleApiFresh = require('./doodle-api');
  try {
    await doodleApiFresh.uploadDoodleImage('/tmp/doodle.png');
    assert.fail('should reject without session');
  } catch (error) {
    assert.strictEqual(error.userMessage, '登录状态已失效', 'missing session surfaces unauthorized');
  }
  Module._load = originalLoad;

  // ===== getLatestDoodleArtUrl =====

  // 8. 多条记录，取最新 DOODLE 的 payload.artUrl（对象 payload）
  reset();
  requestResponses = [[
    { actionType: 'NICKNAME', payload: '{}', actionDate: '2026-08-09' },
    { actionType: 'DOODLE', payload: { artUrl: 'https://oss.example/doodles/old.png' }, actionDate: '2026-08-09' },
    { actionType: 'DOODLE', payload: { artUrl: 'https://oss.example/doodles/new.png' }, actionDate: '2026-08-10' }
  ]];
  const latestUrl = await doodleApi.getLatestDoodleArtUrl('pet-1');
  assert.strictEqual(latestUrl, 'https://oss.example/doodles/new.png', 'getLatestDoodleArtUrl returns latest DOODLE artUrl');
  assert.strictEqual(requests.gets[0], '/pet/pet-1/hatch-actions', 'getLatestDoodleArtUrl uses correct endpoint');

  // 9. 兼容 action_type 命名
  reset();
  requestResponses = [[
    { action_type: 'DOODLE', payload: { artUrl: 'https://oss.example/doodles/underscore.png' }, actionDate: '2026-08-10' }
  ]];
  const underscored = await doodleApi.getLatestDoodleArtUrl('pet-1');
  assert.strictEqual(underscored, 'https://oss.example/doodles/underscore.png', 'getLatestDoodleArtUrl supports action_type');

  // 10. 兼容字符串 payload
  reset();
  requestResponses = [[
    { actionType: 'DOODLE', payload: '{"artUrl":"https://oss.example/doodles/json.png"}', actionDate: '2026-08-10' }
  ]];
  const jsonPayload = await doodleApi.getLatestDoodleArtUrl('pet-1');
  assert.strictEqual(jsonPayload, 'https://oss.example/doodles/json.png', 'getLatestDoodleArtUrl parses string payload');

  // 11. 无 DOODLE 记录 → ''
  reset();
  requestResponses = [[
    { actionType: 'NICKNAME', payload: '{}', actionDate: '2026-08-10' }
  ]];
  const none = await doodleApi.getLatestDoodleArtUrl('pet-1');
  assert.strictEqual(none, '', 'getLatestDoodleArtUrl returns empty when no DOODLE');

  // 12. payload 缺失 artUrl → ''
  reset();
  requestResponses = [[
    { actionType: 'DOODLE', payload: { other: 'x' }, actionDate: '2026-08-10' }
  ]];
  const noArtUrl = await doodleApi.getLatestDoodleArtUrl('pet-1');
  assert.strictEqual(noArtUrl, '', 'getLatestDoodleArtUrl returns empty when artUrl missing');

  // 13. 请求异常 → 抛出
  reset();
  requestResponses = [new Error('network')];
  try {
    await doodleApi.getLatestDoodleArtUrl('pet-1');
    assert.fail('should reject on request error');
  } catch (error) {
    assert.strictEqual(error.message, 'network', 'getLatestDoodleArtUrl propagates request error');
  }

  console.log('doodle-api.test.js: ALL PASS');
})().finally(() => { Module._load = originalLoad; });
