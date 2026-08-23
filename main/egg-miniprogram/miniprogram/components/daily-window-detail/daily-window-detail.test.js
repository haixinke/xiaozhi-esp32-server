// daily-window-detail 组件测试：窗景远程图 downloadFile 通道（缓存/失败/乱序/降级）
const assert = require('assert');
const Module = require('module');

const componentPath = require.resolve('./daily-window-detail');
const originalLoad = Module._load;
const originalComponent = global.Component;
let componentConfig;

Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === componentPath) {
    if (request === '../../utils/window-weather-canvas') {
      return { createParticles: () => [], drawFrame: () => {}, needsAnimation: () => false };
    }
  }
  return originalLoad.call(this, request, parent, isMain);
};
global.Component = (config) => { componentConfig = config; };

require('./daily-window-detail');
assert.ok(componentConfig, 'component registered');
Module._load = originalLoad;
global.Component = originalComponent;

const events = [];
function makeInstance(image) {
  return {
    data: { ...componentConfig.data },
    properties: { visible: true, image: image || '', weather: 'sunny', season: 'spring', period: 'day', lightPhase: 'midday', originStyle: '' },
    setData(changes, cb) { this.data = { ...this.data, ...changes }; if (cb) cb(); },
    triggerEvent(name, detail) { events.push({ name, detail }); },
    // 桩掉画布选择器，避免 setupCanvas 触达真实 wx 查询
    createSelectorQuery() {
      return { select() { return this; }, fields() { return this; }, exec() {} };
    },
    ...componentConfig.methods
  };
}

// 无 wx 环境降级：直连 URL
const fallbackInstance = makeInstance('https://oss.example/window.webp');
fallbackInstance.prepareContent('https://oss.example/window.webp');
assert.strictEqual(fallbackInstance.data.displayImage, 'https://oss.example/window.webp', 'no-wx fallback binds the remote URL directly');
assert.strictEqual(fallbackInstance.data.loading, true, 'loading state set while image pending');

// 挂 wx.downloadFile mock
const deferredDownloads = {};
const downloadCalls = [];
global.wx = {
  downloadFile(opts) {
    downloadCalls.push(opts.url);
    if (opts.url.includes('deferred')) {
      deferredDownloads[opts.url] = opts;
      return;
    }
    if (opts.url.includes('fail')) {
      opts.fail({ errMsg: 'mock fail' });
      return;
    }
    opts.success({ statusCode: 200, tempFilePath: 'wxfile://tmp/' + opts.url.split('/').pop() });
  }
};

// 成功：落地本地路径；同 URL 第二次命中会话缓存
const okInstance = makeInstance('https://oss.example/w_a.webp');
okInstance.resolveWindowImage('https://oss.example/w_a.webp');
assert.strictEqual(okInstance.data.displayImage, 'wxfile://tmp/w_a.webp', 'downloadFile success feeds the local temp path');
okInstance.resolveWindowImage('https://oss.example/w_a.webp');
assert.strictEqual(downloadCalls.filter(u => u === 'https://oss.example/w_a.webp').length, 1, 'second resolve of same URL hits the session cache');
okInstance.onImageLoad();
assert.strictEqual(okInstance.data.loading, false, 'image load clears the loading state');

// 失败：进入 failed 态，loading 复位
const failInstance = makeInstance('https://oss.example/fail_w.webp');
failInstance.resolveWindowImage('https://oss.example/fail_w.webp');
assert.strictEqual(failInstance.data.failed, true, 'download failure enters the failed state');

// 乱序防护：旧 URL 迟到回调不得覆盖
const raceInstance = makeInstance('https://oss.example/deferred_a.webp');
raceInstance.resolveWindowImage('https://oss.example/deferred_a.webp');
raceInstance.resolveWindowImage('https://oss.example/deferred_b.webp');
deferredDownloads['https://oss.example/deferred_a.webp'].success({ statusCode: 200, tempFilePath: 'wxfile://tmp/a.webp' });
assert.strictEqual(raceInstance.data.displayImage, '', 'stale callback for the old URL is discarded');
deferredDownloads['https://oss.example/deferred_b.webp'].success({ statusCode: 200, tempFilePath: 'wxfile://tmp/b.webp' });
assert.strictEqual(raceInstance.data.displayImage, 'wxfile://tmp/b.webp', 'latest URL callback wins');

// 重试：失败后重新发起 download（mock 同步再失败，终态回到 failed）
const retryInstance = makeInstance('https://oss.example/fail_w.webp');
retryInstance.resolveWindowImage('https://oss.example/fail_w.webp');
assert.strictEqual(retryInstance.data.failed, true);
const callsBefore = downloadCalls.filter(u => u === 'https://oss.example/fail_w.webp').length;
retryInstance.onRetry();
assert.strictEqual(downloadCalls.filter(u => u === 'https://oss.example/fail_w.webp').length, callsBefore + 1, 'retry issues a fresh download');

delete global.wx;
console.log('daily-window-detail.test.js: ALL PASS');
