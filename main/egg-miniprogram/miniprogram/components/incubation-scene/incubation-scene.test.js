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

// 背景图加载失败进入错误态
componentConfig.methods.onFullSceneImageError.call(instance);
assert.strictEqual(instance.data.fullSceneImageFailed, true);
// 重试触发 retryscene 并复位错误态
componentConfig.methods.onRetryFullSceneImage.call(instance);
assert.strictEqual(instance.data.fullSceneImageFailed, false);
assert.strictEqual(events[events.length - 1].name, 'retryscene');

console.log('incubation-scene.test.js: ALL PASS');
