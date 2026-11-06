// 使用真实注册的收藏卡/一起画页面，验证门槛异步恢复不会吞掉画布初始化和资源清理。
const assert = require('node:assert/strict');
const Module = require('module');
const originalLoad = Module._load;
const originals = Object.fromEntries(['wx', 'Page', 'getApp', 'getCurrentPages'].map((key) => [key, global[key]]));
let definition;
let loginPromise;
let session = { userId: 42, hasPhone: true };
let cardDraws = 0;
let drawingInitializations = 0;
let drawingDisposals = 0;
let gameStarts = 0;
let redirects = 0;
const pet = { id: 'fixture-pet', hatchStatus: 'HATCHED', prototype: 'RABBIT',
  collectionCards: [{ id: 'fixture-card', brief: 'test' }] };
const app = { globalData: {}, ensureLogin: () => loginPromise || Promise.resolve(session) };
const petStore = {
  getPet: () => pet, getPetById: () => pet, getPetSnapshot: () => ({ pet, pets: [pet] }),
  refreshPets: async () => ({ pet, pets: [pet] }), todayKey: () => '2026-11-05'
};
const canvasContext = new Proxy({
  createLinearGradient: () => ({ addColorStop() {} }),
  measureText: (text) => ({ width: String(text).length * 10 }),
  draw() { cardDraws += 1; }
}, { get: (target, key) => target[key] || (() => {}) });
Module._load = function (request, parent, isMain) {
  if (request === './auth' && parent && parent.filename.endsWith('/phone-gate.js')) return {
    getPhoneBindingContext: () => ({ userId: session.userId, generation: 0 }),
    isPhoneBindingContextCurrent: () => true
  };
  if (request.endsWith('/pet-store')) return petStore;
  if (request.endsWith('/invite-api')) return { getMine: async () => null };
  if (request === '../../utils/drawing-api') return {
    config: async () => ({ earnStars: 0, consumeStars: 0, remainingPlays: 2 }),
    start: async () => { gameStarts += 1; return { sessionId: 'fixture-session', referenceId: 'short_pencil' }; }
  };
  if (request === '../../utils/drawing-canvas') return { createCanvasRenderer: () => ({
    init: async () => { drawingInitializations += 1; }, render() {},
    dispose() { drawingDisposals += 1; }
  }) };
  return originalLoad.call(this, request, parent, isMain);
};
global.Page = (value) => { definition = value; };
global.getApp = () => app;
global.getCurrentPages = () => [];
global.wx = {
  createCanvasContext: () => canvasContext,
  getImageInfo: (options) => options.fail(),
  getSystemInfoSync: () => ({}),
  getAppAuthorizeSetting: () => ({}),
  enableAlertBeforeUnload() {}, disableAlertBeforeUnload() {},
  showToast() {}, reLaunch() { redirects += 1; }
};
function page() {
  return { ...definition, data: JSON.parse(JSON.stringify(definition.data)),
    setData(patch, callback) { this.data = { ...this.data, ...patch }; if (callback) callback(); } };
}

(async () => {
  require('../miniprogram/pages/collection-card/collection-card');
  let resolveLogin;
  loginPromise = new Promise((resolve) => { resolveLogin = resolve; });
  const card = page();
  const cardLoad = card.onLoad({});
  const cardReady = card.onReady();
  assert.equal(cardDraws, 0, 'card canvas waits for account verification');
  resolveLogin(session);
  await Promise.all([cardLoad, cardReady]);
  assert.equal(cardDraws, 1, 'card onReady runs after account verification and card loading');
  assert.equal(card.data.card.name, 'RABBIT');

  // 登录检查期间暂时离开：原始参数和尚未执行的 load/ready 应在返回时恢复一次。
  loginPromise = new Promise((resolve) => { resolveLogin = resolve; });
  const suspendedCard = page();
  const suspendedCardLoad = suspendedCard.onLoad({ new: '1', index: '0' });
  const suspendedCardReady = suspendedCard.onReady();
  suspendedCard.onHide();
  resolveLogin(session);
  await Promise.all([suspendedCardLoad, suspendedCardReady]);
  assert.equal(suspendedCard.data.card, null, 'hidden card does not initialize behind the next page');
  await suspendedCard.onShow();
  assert.equal(suspendedCard.data.isNew, true, 'resumed card loading preserves its route parameters');
  assert.equal(cardDraws, 2, 'resumed card receives its deferred onReady once');
  suspendedCard.onHide();
  await suspendedCard.onShow();
  assert.equal(cardDraws, 2, 'returning again does not repeat an already completed onReady');

  loginPromise = null;
  require('../miniprogram/drawing/pages/draw/draw');
  const drawing = page();
  await Promise.all([drawing.onLoad(), drawing.onShow(), drawing.onReady()]);
  assert.equal(drawing.data.canvasReady, true, 'drawing canvas initializes after the gate');
  assert.equal(drawingInitializations, 1);
  assert.equal(gameStarts, 1, 'drawing starts only once across load, show and ready');
  drawing.onHide();
  drawing.onUnload();
  assert.equal(drawingDisposals, 1, 'guarded unload still releases the real page renderer');

  loginPromise = new Promise((resolve) => { resolveLogin = resolve; });
  const suspendedDrawing = page();
  const suspendedDrawingLoad = suspendedDrawing.onLoad();
  const suspendedDrawingReady = suspendedDrawing.onReady();
  suspendedDrawing.onHide();
  resolveLogin(session);
  await Promise.all([suspendedDrawingLoad, suspendedDrawingReady]);
  assert.equal(gameStarts, 1, 'hidden drawing does not start a game after a late gate response');
  await suspendedDrawing.onShow();
  assert.equal(suspendedDrawing.data.canvasReady, true, 'returning resumes deferred drawing load and ready');
  assert.equal(gameStarts, 2, 'resumed drawing starts exactly one game');
  suspendedDrawing.onHide();
  await suspendedDrawing.onShow();
  assert.equal(gameStarts, 2, 'a completed drawing initialization is never replayed');
  suspendedDrawing.onUnload();

  loginPromise = new Promise((resolve) => { resolveLogin = resolve; });
  const disposedDrawing = page();
  const disposedLoad = disposedDrawing.onLoad();
  const disposedReady = disposedDrawing.onReady();
  disposedDrawing.onUnload();
  resolveLogin(session);
  await Promise.all([disposedLoad, disposedReady]);
  await disposedDrawing.onShow();
  assert.equal(gameStarts, 2, 'unload permanently cancels deferred initialization');
  assert.equal(drawingInitializations, 2, 'unloaded pages never allocate a renderer after the gate returns');

  loginPromise = null;
  session = { userId: 42, hasPhone: false };
  const blockedDrawing = page();
  await Promise.all([blockedDrawing.onLoad(), blockedDrawing.onReady(), blockedDrawing.onShow()]);
  assert.equal(gameStarts, 2, 'unbound hatched users cannot start a game from a direct subpackage route');
  assert.equal(drawingInitializations, 2, 'blocked pages do not initialize business resources');
  assert.equal(redirects, 1);
  console.log('phone-gate-pages.test.js: ALL PASS');
})().finally(() => {
  Module._load = originalLoad;
  Object.entries(originals).forEach(([key, value]) => { global[key] = value; });
}).catch((error) => { console.error(error); process.exitCode = 1; });
