const assert = require('assert');
const path = require('path');
const Module = require('module');

const componentPath = require.resolve('./incubation-scene');
const originalLoad = Module._load;
const originalComponent = global.Component;
let componentConfig;

Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === componentPath) {
    if (request === '../../utils/window-weather-canvas') {
      return { createParticles: () => [], drawFrame: () => {}, needsAnimation: () => false };
    }
    if (request === '../../utils/canvas-2d') return { createLayer: () => Promise.resolve(null) };
  }
  return originalLoad.call(this, request, parent, isMain);
};
global.Component = (config) => { componentConfig = config; };

require('./incubation-scene');
assert.ok(componentConfig, 'component registered');
assert.deepStrictEqual(Object.keys(componentConfig.properties).sort(),
  ['eggArtUrl', 'environment', 'lampOn'].sort());

const events = [];
const instance = {
  data: { ...componentConfig.data },
  properties: { environment: null, eggArtUrl: '', lampOn: false },
  setData(changes) { this.data = { ...this.data, ...changes }; },
  triggerEvent(name, detail) { events.push({ name, detail }); },
  ...componentConfig.methods
};

// 蛋点击/长按事件
componentConfig.methods.onEggTap.call(instance);
componentConfig.methods.onEggCuddle.call(instance);
assert.deepStrictEqual(events.map(e => e.name), ['eggtap', 'eggcuddle']);
// 点蛋触发左右晃动态（对齐静态 UI 项目 wobble 动效）
assert.strictEqual(instance.data.eggWobbling, true, 'egg tap turns on the wobble state');
assert.ok(instance.wobbleTimer, 'a timer is scheduled to reset the wobble state');
clearTimeout(instance.wobbleTimer); // 释放定时器，避免占用事件循环

// 背景图加载失败进入错误态
componentConfig.methods.onFullSceneImageError.call(instance);
assert.strictEqual(instance.data.fullSceneImageFailed, true);
// 重试触发 retryscene 并复位错误态
componentConfig.methods.onRetryFullSceneImage.call(instance);
assert.strictEqual(instance.data.fullSceneImageFailed, false);
assert.strictEqual(events[events.length - 1].name, 'retryscene');

// 看门狗：首屏背景图挂起（既不 load 也不 error）超时后落入错误态
const hangingInstance = {
  data: { ...componentConfig.data },
  properties: { environment: { sceneKey: 'summer_clear_night', fullSceneImage: 'https://oss.example/night.webp' }, eggArtUrl: '', lampOn: false },
  setData(changes) { this.data = { ...this.data, ...changes }; },
  triggerEvent(name, detail) { events.push({ name, detail }); },
  ...componentConfig.methods
};
componentConfig.methods.applySceneChange.call(hangingInstance);
assert.ok(hangingInstance.sceneLoadTimer, 'watchdog armed when a full scene image is pending');
componentConfig.methods.onSceneLoadTimeout.call(hangingInstance);
assert.strictEqual(hangingInstance.data.fullSceneImageFailed, true, 'pending first image falls into the error state after timeout');
clearTimeout(hangingInstance.sceneLoadTimer);
// 错误态下重试：复位错误态并重新武装看门狗
componentConfig.methods.onRetryFullSceneImage.call(hangingInstance);
assert.strictEqual(hangingInstance.data.fullSceneImageFailed, false);
assert.ok(hangingInstance.sceneLoadTimer, 'retry re-arms the watchdog');
clearTimeout(hangingInstance.sceneLoadTimer);

// 看门狗：已有旧图成功垫底时，新图挂起静默保留旧场景，不打扰用户
const loadedInstance = {
  data: { ...componentConfig.data },
  properties: { environment: { sceneKey: 'summer_clear_day', fullSceneImage: 'https://oss.example/day.webp' }, eggArtUrl: '', lampOn: false },
  setData(changes) { this.data = { ...this.data, ...changes }; },
  triggerEvent(name, detail) { events.push({ name, detail }); },
  ...componentConfig.methods
};
componentConfig.methods.applySceneChange.call(loadedInstance);
componentConfig.methods.onFullSceneImageLoad.call(loadedInstance);
assert.strictEqual(loadedInstance.sceneLoadTimer, null, 'successful load disarms the watchdog');
clearTimeout(loadedInstance.crossfadeTimer); // 释放交叉淡化定时器，避免占用事件循环
loadedInstance.properties = { ...loadedInstance.properties, environment: { sceneKey: 'summer_clear_night', fullSceneImage: 'https://oss.example/night.webp' } };
componentConfig.methods.applySceneChange.call(loadedInstance);
assert.ok(loadedInstance.sceneLoadTimer, 'watchdog re-arms on period switch');
componentConfig.methods.onSceneLoadTimeout.call(loadedInstance);
assert.strictEqual(loadedInstance.data.fullSceneImageFailed, false, 'previous loaded image stays as fallback, no error page');
clearTimeout(loadedInstance.sceneLoadTimer);

// downloadFile 通道：成功落地本地路径、会话内缓存复用、失败分级、乱序防护
function makeInstance(environment) {
  return {
    data: { ...componentConfig.data },
    properties: { environment: environment || null, eggArtUrl: '', lampOn: false },
    setData(changes) { this.data = { ...this.data, ...changes }; },
    triggerEvent(name, detail) { events.push({ name, detail }); },
    ...componentConfig.methods
  };
}
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
const remoteInstance = makeInstance(null);
remoteInstance.loadRemoteImage('https://oss.example/bg.webp', 'localFullSceneImage');
assert.strictEqual(remoteInstance.data.localFullSceneImage, 'wxfile://tmp/bg.webp', 'downloadFile success feeds the local temp path');
remoteInstance.loadRemoteImage('https://oss.example/bg.webp', 'localFullSceneImage');
assert.strictEqual(downloadCalls.filter(u => u === 'https://oss.example/bg.webp').length, 1, 'second load of same URL hits the session cache');
// 失败分级：整幅背景进错误态，辅助素材静默
remoteInstance.loadRemoteImage('https://oss.example/fail_bg.webp', 'localFullSceneImage');
assert.strictEqual(remoteInstance.data.fullSceneImageFailed, true, 'full scene download failure enters the error state');
const quietInstance = makeInstance(null);
quietInstance.loadRemoteImage('https://oss.example/fail_nest.webp', 'localNestImage');
assert.strictEqual(quietInstance.data.fullSceneImageFailed, false, 'aux asset failure stays silent');
// 乱序防护：旧 URL 的迟到回调不得覆盖槽位
const raceInstance = makeInstance(null);
raceInstance.loadRemoteImage('https://oss.example/deferred_a.webp', 'localFullSceneImage');
raceInstance.loadRemoteImage('https://oss.example/deferred_b.webp', 'localFullSceneImage');
deferredDownloads['https://oss.example/deferred_a.webp'].success({ statusCode: 200, tempFilePath: 'wxfile://tmp/a.webp' });
assert.strictEqual(raceInstance.data.localFullSceneImage, '', 'stale callback for the old URL is discarded');
deferredDownloads['https://oss.example/deferred_b.webp'].success({ statusCode: 200, tempFilePath: 'wxfile://tmp/b.webp' });
assert.strictEqual(raceInstance.data.localFullSceneImage, 'wxfile://tmp/b.webp', 'latest URL callback wins');
delete global.wx;

// wxml 蛋元素绑定 wobble 类
const fs = require('fs');
const sceneWxml = fs.readFileSync(path.join(__dirname, 'incubation-scene.wxml'), 'utf8');
assert.ok(sceneWxml.includes("eggWobbling ? 'egg--wobble' : ''"), 'egg element binds the egg--wobble class');
assert.ok(sceneWxml.includes('src="{{localEggArt || localEggImage}}"'), 'egg binds the downloaded local artwork over the local egg image');
assert.ok(sceneWxml.includes('src="{{localFullSceneImage}}"'), 'full scene image binds the downloaded local path');
assert.ok(sceneWxml.includes('src="{{localNestImage}}"'), 'nest image binds the downloaded local path');
assert.ok(!sceneWxml.includes('wx:if="{{eggArtUrl}}"'), 'scene does not render a second full egg image on top of the base egg');
const sceneWxss = fs.readFileSync(path.join(__dirname, 'incubation-scene.wxss'), 'utf8');
assert.ok(sceneWxss.includes('@keyframes wobble'), 'wobble keyframes defined');
assert.ok(sceneWxss.includes('.egg.egg--wobble'), 'egg--wobble class defined');

console.log('incubation-scene.test.js: ALL PASS');
