// 以真实页面定义验证资格和完成结算，接口仅替换网络边界。
const assert = require('node:assert/strict');
let pet = null;
let starts = 0;
let confirm = false;
let view;
let canvasFails = false;
let completeFails = false;
let completions = 0;
let navigations = 0;
let audioPlayed = 0;
const modalContents = [];
const exitMessages = [];
let loadConfig = async () => ({ earnStars: 0, consumeStars: 3, remainingPlays: 2 });
let startError = null;
let startHandler = null;
function stub(modulePath, exports) {
  const id = require.resolve(modulePath);
  require.cache[id] = { id, filename: id, loaded: true, exports };
}
stub('../../../utils/pet-store', { getPetSnapshot: () => ({ pet }) });
stub('../../utils/drawing-api', {
  config: () => loadConfig(),
  start: async body => {
    if (startHandler) return startHandler(body);
    if (startError) throw startError;
    starts += 1; return { sessionId: 's', referenceId: 'short_pencil' };
  },
  complete: async () => {
    completions += 1;
    if (completeFails) throw new Error('offline');
    return { sessionId: 's', awardedStars: 2 };
  }
});
stub('../../utils/drawing-canvas', { createCanvasRenderer: () => ({
  init: async () => { if (canvasFails) throw new Error('not ready'); }, render() {}, dispose() {},
  point: touch => touch ? ({ x: touch.x, y: touch.y, t: touch.t }) : null
}) });
global.getCurrentPages = () => [{}, {}];
global.wx = {
  showToast() {},
  showModal: options => { modalContents.push(options.content); options.success({ confirm }); },
  getAppAuthorizeSetting: () => ({}),
  getSystemInfoSync: () => ({}),
  enableAlertBeforeUnload(options) { exitMessages.push(options.message); }, disableAlertBeforeUnload() {},
  navigateBack() { navigations += 1; }, switchTab() { navigations += 1; },
  vibrateShort() {},
  createInnerAudioContext: () => ({ onError() {}, play() { audioPlayed += 1; }, destroy() {} })
};
const definition = require('./draw');
function page() {
  view = { ...definition, data: JSON.parse(JSON.stringify(definition.data)),
    setData(patch, callback) { this.data = { ...this.data, ...patch }; if (callback) callback(); } };
  return view;
}
(async () => {
  await page().onLoad();
  await view.onReady();
  assert.equal(starts, 0);
  assert.match(view.data.errorText, /破壳/);
  pet = { id: 'p', hatchStatus: 'HATCHED' };
  await page().onLoad();
  await view.onReady();
  assert.equal(starts, 1, '开局不以当前配置金额要求确认或扣费');
  assert.deepEqual(modalContents, [], '开局不承诺费用或奖励金额');
  assert.equal(view.data.canvasReady, true, '开局前先准备画纸');
  confirm = true;
  await view.onRetry();
  assert.equal(starts, 1);
  assert.equal(view.data.reference.name, '短铅笔');
  view.onUnload();
  canvasFails = true;
  await page().onLoad();
  await view.onReady();
  assert.equal(starts, 1, '画纸初始化失败不得占用开局次数');
  canvasFails = false;
  await view.onRetry();
  assert.equal(view.data.canvasReady, true);
  assert.equal(starts, 2, '初始化失败恢复后才能开局');
  view.onReferenceLoad();
  assert.equal(view.data.peekVisible, true);
  view.onDismissPeek();
  await view.onFinish();
  assert.equal(completions, 0, '未达标不得向后端结算');
  view.onTool({ currentTarget: { dataset: { tool: 'brush' } } });
  view.onColorPicker();
  view.onColor({ currentTarget: { dataset: { color: '#7B3E52' } } });
  view.onSize({ currentTarget: { dataset: { size: 12 } } });
  view.onTool({ currentTarget: { dataset: { tool: 'eraser' } } });
  view.onSize({ currentTarget: { dataset: { size: 22 } } });
  view.onTool({ currentTarget: { dataset: { tool: 'sticker' } } });
  view.onSticker({ currentTarget: { dataset: { sticker: 'heart' } } });
  view.onTouchStart({ touches: [{ x: 0.3, y: 0.3, t: 0 }] });
  view.onTouchEnd({ touches: [] });
  assert.equal(view.data.hasContent, true);
  view.onClear();
  assert.equal(view.data.hasContent, false);
  view.onUndo();
  assert.equal(view.data.hasContent, true, '页面可撤销清空');
  view.onClear();
  view.onTool({ currentTarget: { dataset: { tool: 'brush' } } });
  view.onTouchStart({ touches: [{ x: 0.1, y: 0.1, t: 0 }] });
  view.onTouchMove({ touches: [{ x: 0, y: 0 }, { x: 20, y: 0 }] });
  view.onTouchMove({ touches: [{ x: 0, y: 0 }, { x: 50, y: 0 }] });
  assert.equal(view.data.canvasScale, 1.6, '双指缩放不超过1.6倍');
  view.onTouchEnd({ touches: [] });
  assert.equal(view.model.evaluate().strokes, 0, '缩放取消当前未完成笔触');
  view.onTouchStart({ touches: [{ x: 0.1, y: 0.1, t: 0 }] });
  view.onTouchCancel();
  for (let row = 0; row < 5; row += 1) {
    view.onTouchStart({ touches: [{ x: 0.1, y: row / 10, t: row * 1200 }] });
    view.onTouchMove({ touches: [{ x: 0.8, y: row / 10, t: row * 1200 + 1000 }] });
    view.onTouchEnd({ touches: [] });
  }
  assert.equal(view.model.evaluate().ok, true);
  view.onPeek();
  view.onHide();
  view.onShow();
  assert.equal(view.data.peekVisible, true, '返回继续看图，而非丢失当前画');
  view.onDismissPeek();
  confirm = false;
  await view.onBack();
  assert.equal(navigations, 0, '取消离开可继续本局');
  completeFails = true;
  await view.onFinish();
  assert.equal(view.data.result, null);
  assert.equal(view.model.evaluate().ok, true, '提交失败保留画布可重试');
  completeFails = false;
  await view.onFinish();
  assert.equal(view.data.result.awardedStars, 2);
  assert.equal(audioPlayed, 1, '服务端结算成功才播放奖励');
  view.onHide(); view.onShow();
  assert.equal(audioPlayed, 1, '隐藏恢复不补播奖励');
  view.onDone();
  assert.equal(navigations, 1);
  assert.equal(completions, 2, '好的仅返回，不再结算');
  view.onUnload();

  // 配置在后台返回后，重新显示页面必须继续初始化，不能留下没有重试按钮的空画纸。
  let resolveConfig;
  loadConfig = () => new Promise(resolve => { resolveConfig = resolve; });
  await page().onLoad();
  const initialization = view.onReady();
  await new Promise(resolve => setImmediate(resolve));
  view.onHide();
  resolveConfig({ earnStars: 0, consumeStars: 0, remainingPlays: 2 });
  await initialization;
  loadConfig = async () => ({ earnStars: 0, consumeStars: 0, remainingPlays: 2 });
  const beforeResume = starts;
  await view.onShow();
  assert.equal(starts, beforeResume + 1, '返回后继续同一开局流程');
  assert.equal(view.data.started, true);
  view.onUnload();

  startError = { type: 'business', code: 10424, userMessage: '服务暂不可用' };
  await page().onLoad();
  await view.onReady();
  assert.equal(view.data.errorText, '服务暂不可用');
  view.onUnload();

  // 已在后端占次但响应丢失时，必须先恢复原局，不能被最新额度查询挡住。
  let configCalls = 0;
  const retryBodies = [];
  startError = null;
  confirm = true;
  loadConfig = async () => {
    configCalls += 1;
    return { earnStars: 0, consumeStars: 3, dailyLimit: 1, remainingPlays: retryBodies.length ? 0 : 1 };
  };
  startHandler = async body => {
    retryBodies.push(body);
    if (retryBodies.length === 1) throw new Error('response lost after commit');
    return { sessionId: 'recovered', referenceId: 'short_pencil' };
  };
  await page().onLoad();
  await view.onReady();
  assert.equal(view.data.started, false);
  await view.onRetry();
  assert.equal(view.data.started, true, '恢复已成功开局不受额度已耗尽阻挡');
  assert.equal(configCalls, 1, '先恢复未知结果，不查询可能停用或额度用尽的新开局配置');
  assert.equal(retryBodies.length, 2);
  assert.deepEqual(retryBodies[1], retryBodies[0], '恢复局次必须沿用原单号');
  assert.deepEqual(Object.keys(retryBodies[0]).sort(), ['petId', 'requestId']);
  assert.equal(modalContents.concat(exitMessages).some(text => /扣星星|不退还|本局消耗|费用/.test(text)), false,
    '开局与退出均不展示未发生的扣费');
  view.onUnload();
  console.log('draw page tests passed');
})().catch(error => { console.error(error); process.exitCode = 1; });
