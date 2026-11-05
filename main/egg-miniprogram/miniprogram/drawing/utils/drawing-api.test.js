// 网络边界只接收完成局次，不向后端传递画作或绘画过程。
const assert = require('node:assert/strict');
const requestId = require.resolve('../../utils/request');
const calls = [];
require.cache[requestId] = { id: requestId, filename: requestId, loaded: true,
  exports: { post: (url, data) => { calls.push({ url, data }); return Promise.resolve({}); } } };
const api = require('./drawing-api');
(async () => {
  await api.complete('session-a', { strokes: [{ points: [{ x: 0.2, y: 0.3, t: 5000 }] }] });
  assert.deepEqual(calls, [{ url: '/app/drawing/session-a/complete', data: undefined }],
    '即使调用方误传画笔数据，完成接口也只发送局次身份');
  console.log('drawing-api tests passed');
})().catch(error => { console.error(error); process.exitCode = 1; });
