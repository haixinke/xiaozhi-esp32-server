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

// wxml 蛋元素绑定 wobble 类
const fs = require('fs');
const sceneWxml = fs.readFileSync(path.join(__dirname, 'incubation-scene.wxml'), 'utf8');
assert.ok(sceneWxml.includes("eggWobbling ? 'egg--wobble' : ''"), 'egg element binds the egg--wobble class');
assert.ok(sceneWxml.includes('src="{{eggArtUrl || environment.eggImage}}"'), 'committed artwork replaces the environment egg image');
assert.ok(!sceneWxml.includes('wx:if="{{eggArtUrl}}"'), 'scene does not render a second full egg image on top of the base egg');
const sceneWxss = fs.readFileSync(path.join(__dirname, 'incubation-scene.wxss'), 'utf8');
assert.ok(sceneWxss.includes('@keyframes wobble'), 'wobble keyframes defined');
assert.ok(sceneWxss.includes('.egg.egg--wobble'), 'egg--wobble class defined');

console.log('incubation-scene.test.js: ALL PASS');
