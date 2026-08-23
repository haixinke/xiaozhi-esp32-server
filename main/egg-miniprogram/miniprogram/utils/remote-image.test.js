// remote-image 工具测试：缓存 / 并发去重 / 失败 / 无 wx 降级
const assert = require('assert');
const { loadRemoteImage } = require('./remote-image');

// 无 wx 环境：降级直连原 URL，且不写缓存
loadRemoteImage('https://oss.example/plain.webp', (result) => {
  assert.strictEqual(result, 'https://oss.example/plain.webp', 'no-wx fallback returns the remote URL');
});
// 空 URL：直接 null
loadRemoteImage('', (result) => {
  assert.strictEqual(result, null, 'empty URL resolves to null');
});

// 挂 wx.downloadFile mock
const deferred = {};
const calls = [];
global.wx = {
  downloadFile(opts) {
    calls.push(opts.url);
    if (opts.url.includes('deferred')) {
      deferred[opts.url] = opts;
      return;
    }
    if (opts.url.includes('fail')) {
      opts.fail({ errMsg: 'mock fail' });
      return;
    }
    opts.success({ statusCode: 200, tempFilePath: 'wxfile://tmp/' + opts.url.split('/').pop() });
  }
};

// 成功：返回本地路径并写缓存，第二次不再下载
let firstResult;
loadRemoteImage('https://oss.example/a.webp', (r) => { firstResult = r; });
assert.strictEqual(firstResult, 'wxfile://tmp/a.webp', 'success resolves the temp path');
let secondResult;
loadRemoteImage('https://oss.example/a.webp', (r) => { secondResult = r; });
assert.strictEqual(secondResult, 'wxfile://tmp/a.webp', 'cached URL resolves the same temp path');
assert.strictEqual(calls.filter((u) => u === 'https://oss.example/a.webp').length, 1, 'cache hit avoids a second download');

// 并发去重：同 URL 两个等待者只发一次下载，都拿到结果
const waiterResults = [];
loadRemoteImage('https://oss.example/deferred_x.webp', (r) => waiterResults.push(r));
loadRemoteImage('https://oss.example/deferred_x.webp', (r) => waiterResults.push(r));
assert.strictEqual(calls.filter((u) => u === 'https://oss.example/deferred_x.webp').length, 1, 'concurrent same-URL requests are deduplicated');
deferred['https://oss.example/deferred_x.webp'].success({ statusCode: 200, tempFilePath: 'wxfile://tmp/x.webp' });
assert.deepStrictEqual(waiterResults, ['wxfile://tmp/x.webp', 'wxfile://tmp/x.webp'], 'all waiters receive the result');

// 失败：回调 null 且不写缓存，下次可重试
let failResult = 'unset';
loadRemoteImage('https://oss.example/fail_y.webp', (r) => { failResult = r; });
assert.strictEqual(failResult, null, 'failure resolves null');
loadRemoteImage('https://oss.example/fail_y.webp', () => {});
assert.strictEqual(calls.filter((u) => u === 'https://oss.example/fail_y.webp').length, 2, 'failure is not cached, retry issues a fresh download');

delete global.wx;
console.log('remote-image.test.js: ALL PASS');
