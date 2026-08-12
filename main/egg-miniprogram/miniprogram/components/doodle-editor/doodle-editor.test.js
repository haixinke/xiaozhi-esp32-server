const assert = require('assert');
const fs = require('fs');
const path = require('path');
const Module = require('module');

const componentPath = require.resolve('./doodle-editor');
const originalComponent = global.Component;
const originalLoad = Module._load;
const originalWx = global.wx;

let componentDefinition;
let saveCalls = [];
let saveImplementation;
const toastTitles = [];

Module._load = function (request, parent, isMain) {
  if (parent && parent.filename === componentPath && request === '../../utils/doodle-save') {
    return {
      saveArtwork(options) {
        saveCalls.push(options);
        return saveImplementation(options);
      }
    };
  }
  return originalLoad.call(this, request, parent, isMain);
};

global.Component = definition => { componentDefinition = definition; };
global.wx = {
  showToast({ title }) { toastTitles.push(title); },
  getWindowInfo() { return { statusBarHeight: 44 }; },
  getSystemInfoSync() { return { statusBarHeight: 44 }; }
};

function deferred() {
  let resolve;
  const promise = new Promise(done => { resolve = done; });
  return { promise, resolve };
}

function context(operations) {
  const events = [];
  const engine = {
    exportArtworkCalls: 0,
    getOperations() { return operations; },
    exportArtwork() {
      this.exportArtworkCalls += 1;
      return Promise.resolve('/tmp/doodle.png');
    },
    endStroke() { return false; }
  };
  return Object.assign({}, componentDefinition.methods, {
    data: Object.assign({}, componentDefinition.data, { petId: 'pet-7' }),
    properties: { petId: 'pet-7' },
    pageActive: true,
    editRevision: 0,
    savedRevision: 0,
    engine,
    events,
    setData(patch, callback) {
      Object.assign(this.data, patch);
      if (callback) callback();
    },
    triggerEvent(name, detail) { events.push({ name, detail }); },
    waitForPageExit() {
      this.setData({ pageTransitionPhase: 'exiting' });
      return Promise.resolve();
    },
    collapseToolPanel() {},
    dismissCanvasNotice() {}
  });
}

async function run() {
  try {
    require('./doodle-editor');
    assert.ok(componentDefinition, '组件定义必须可加载');

    const noAutoSave = context([{ type: 'sticker', pattern: 'star' }]);
    let scheduledTimers = 0;
    const originalSetTimeout = global.setTimeout;
    global.setTimeout = () => { scheduledTimers += 1; return scheduledTimers; };
    try {
      noAutoSave.markDirty();
    } finally {
      global.setTimeout = originalSetTimeout;
    }
    assert.strictEqual(noAutoSave.editRevision, 1, '编辑后必须推进当前版本');
    assert.strictEqual(noAutoSave.savedRevision, 0, '编辑本身不得推进已保存版本');
    assert.strictEqual(noAutoSave.data.saveStatus, 'unsaved', '编辑后必须等待用户手动保存');
    assert.strictEqual(noAutoSave.data.saveStatusText, '保存', '未保存状态胶囊必须显示“保存”');
    assert.strictEqual(scheduledTimers, 0, 'markDirty 不得安排自动保存计时器');
    assert.strictEqual(saveCalls.length, 0, '编辑后不得自动调用持久化事务');

    const successResult = {
      ok: true,
      petId: 'pet-7',
      artUrl: 'https://example.com/doodle.png',
      operations: [{ type: 'sticker', pattern: 'star' }],
      empty: false
    };
    saveImplementation = async options => {
      assert.strictEqual(await options.exportArtwork(), '/tmp/doodle.png', '保存事务必须拿到画布导出函数');
      return successResult;
    };
    const saveTask = noAutoSave.onManualSave();
    assert.strictEqual(noAutoSave.data.saveStatusText, '保存中…', '事务期间必须显示保存中');
    assert.strictEqual((await saveTask).ok, true, '手动保存成功必须返回事务结果');
    assert.strictEqual(noAutoSave.savedRevision, 1, '事务成功后才能推进已保存版本');
    assert.strictEqual(noAutoSave.data.saveStatusText, '已保存', '当前版本保存成功后必须显示已保存');
    assert.strictEqual(noAutoSave.engine.exportArtworkCalls, 1, '非空画作只导出一次');
    assert.strictEqual(saveCalls[0].petId, 'pet-7', '保存事务必须收到当前宠物 id');
    assert.deepStrictEqual(saveCalls[0].operations, successResult.operations, '保存事务必须收到当前操作快照');
    assert.deepStrictEqual(noAutoSave.events, [{ name: 'saved', detail: successResult }], '事务成功必须原样抛出 saved 结果');
    await noAutoSave.onManualSave();
    assert.strictEqual(saveCalls.length, 1, '没有新修改时点击已保存胶囊不得重复持久化');

    const failed = context([{ type: 'stroke' }]);
    failed.markDirty();
    saveImplementation = async () => ({ ok: false, message: '网络开小差了' });
    const failedResult = await failed.onManualSave();
    assert.strictEqual(failedResult.ok, false, '事务失败必须返回失败结果');
    assert.strictEqual(failed.savedRevision, 0, '事务失败不得推进已保存版本');
    assert.strictEqual(failed.data.saveStatus, 'error', '事务失败必须进入可重试状态');
    assert.strictEqual(failed.data.saveStatusText, '重新保存', '事务失败胶囊必须显示重新保存');
    assert.strictEqual(failed.events.length, 0, '事务失败不得抛出 saved 事件');
    assert.strictEqual(toastTitles.pop(), '网络开小差了', '事务失败必须给出用户可见反馈');

    const racing = context([{ type: 'stroke', id: 'before-save' }]);
    racing.markDirty();
    const transaction = deferred();
    saveImplementation = () => transaction.promise;
    const racingTask = racing.onManualSave();
    racing.markDirty();
    const racingResult = {
      ok: true,
      petId: 'pet-7',
      artUrl: 'https://example.com/old-revision.png',
      operations: [{ type: 'stroke', id: 'before-save' }],
      empty: false
    };
    transaction.resolve(racingResult);
    await racingTask;
    assert.strictEqual(racing.savedRevision, 1, '竞态成功只能推进到事务开始时的版本');
    assert.strictEqual(racing.editRevision, 2, '保存期间的新编辑必须保留为新版本');
    assert.strictEqual(racing.data.saveStatus, 'unsaved', '保存期间的新编辑在旧事务成功后仍必须未保存');
    assert.strictEqual(racing.data.saveStatusText, '保存', '竞态后必须恢复手动保存入口');
    assert.deepStrictEqual(racing.events, [{ name: 'saved', detail: racingResult }], '已提交的旧版本仍必须通知事务成功');

    const empty = context([]);
    empty.markDirty();
    const emptyResult = { ok: true, petId: 'pet-7', artUrl: '', operations: [], empty: true };
    saveImplementation = async () => emptyResult;
    await empty.onManualSave();
    assert.deepStrictEqual(empty.events, [{ name: 'saved', detail: emptyResult }], '清空作品保存成功必须携带 empty 和空 artUrl');

    const savingBack = context([{ type: 'stroke', id: 'saving' }]);
    savingBack.markDirty();
    const savingTransaction = deferred();
    saveImplementation = () => savingTransaction.promise;
    const activeSave = savingBack.onManualSave();
    const activeBack = savingBack.onBack();
    assert.strictEqual(savingBack.data.exitConfirmVisible, false, '保存中快速返回不得弹出放弃修改确认');
    const savingResult = {
      ok: true,
      petId: 'pet-7',
      artUrl: 'https://example.com/saving.png',
      operations: [{ type: 'stroke', id: 'saving' }],
      empty: false
    };
    savingTransaction.resolve(savingResult);
    await Promise.all([activeSave, activeBack]);
    assert.deepStrictEqual(
      savingBack.events,
      [{ name: 'saved', detail: savingResult }, { name: 'close', detail: undefined }],
      '保存中返回必须等待事务成功后直接关闭'
    );

    const savedBack = context([]);
    await savedBack.onBack();
    assert.deepStrictEqual(savedBack.events, [{ name: 'close', detail: undefined }], '无未保存修改时返回必须直接关闭');

    const dirtyBack = context([{ type: 'stroke' }]);
    dirtyBack.markDirty();
    const callsBeforeBack = saveCalls.length;
    await dirtyBack.onBack();
    assert.strictEqual(dirtyBack.data.exitConfirmVisible, true, '有未保存修改时返回必须显示二次确认');
    assert.strictEqual(dirtyBack.events.length, 0, '用户确认前不得关闭编辑器');
    assert.strictEqual(saveCalls.length, callsBeforeBack, '点击返回不得隐式保存');
    dirtyBack.onContinueEditing();
    assert.strictEqual(dirtyBack.data.exitConfirmVisible, false, '继续画必须关闭确认弹层');
    await dirtyBack.onBack();
    const saveAndExitResult = {
      ok: true,
      petId: 'pet-7',
      artUrl: 'https://example.com/save-and-exit.png',
      operations: [{ type: 'stroke' }],
      empty: false
    };
    saveImplementation = async () => saveAndExitResult;
    await dirtyBack.onSaveAndExit();
    assert.deepStrictEqual(
      dirtyBack.events,
      [{ name: 'saved', detail: saveAndExitResult }, { name: 'close', detail: undefined }],
      '保存并返回必须在保存成功后关闭编辑器'
    );
    assert.strictEqual(saveCalls.length, callsBeforeBack + 1, '保存并返回必须复用手动保存事务');

    const saveAndExitFailure = context([{ type: 'stroke' }]);
    saveAndExitFailure.markDirty();
    await saveAndExitFailure.onBack();
    saveImplementation = async () => ({ ok: false, message: '保存失败，请检查网络' });
    await saveAndExitFailure.onSaveAndExit();
    assert.strictEqual(saveAndExitFailure.data.exitConfirmVisible, true, '保存失败必须保留确认弹层');
    assert.strictEqual(saveAndExitFailure.data.exitConfirmSaving, false, '保存失败必须恢复确认框操作');
    assert.strictEqual(saveAndExitFailure.data.exitConfirmErrorText, '保存失败，请检查网络', '保存失败必须展示弹层错误');
    assert.deepStrictEqual(saveAndExitFailure.events, [], '保存失败不得关闭编辑器');

    const wxml = fs.readFileSync(path.join(__dirname, 'doodle-editor.wxml'), 'utf8');
    const script = fs.readFileSync(path.join(__dirname, 'doodle-editor.js'), 'utf8');
    const wxss = fs.readFileSync(path.join(__dirname, 'doodle-editor.wxss'), 'utf8');
    assert.ok(wxml.includes('bindtap="onManualSave"'), '左上状态胶囊必须是手动保存入口');
    assert.ok(wxml.includes('bindtap="onBack"'), '返回按钮必须使用未保存确认流程');
    assert.ok(wxml.includes('<root-portal') && wxml.includes('<cover-view'), '确认层必须通过 cover-view 门户盖住原生 Canvas');
    assert.ok(!wxml.includes('aria-role="dialog"'), 'cover-view 不支持 dialog 角色，确认层不得使用无效语义');
    assert.strictEqual((wxml.match(/hidden="\{\{exitConfirmVisible\}\}"/g) || []).length, 2, '确认层出现时必须隐藏两个原生 Canvas，避免蛋图遮住弹框');
    assert.ok(wxml.includes('继续画') && wxml.includes('保存并返回'), '确认层必须提供继续画和保存并返回');
    assert.ok(!wxml.includes('不保存返回'), '确认层不得提供不保存返回');
    assert.ok(!wxml.includes('自动保存'), '模板不得暗示自动保存');
    assert.ok(!script.includes('AUTO_SAVE_DELAY') && !script.includes('scheduleAutoSave') && !script.includes('autoSaveTimer'), '组件必须彻底移除自动保存计时器');
    assert.match(wxss, /\.exit-confirm-overlay\s*\{[^}]*z-index:\s*9999/s, '确认遮罩必须高于 Canvas 和工具栏');
    assert.match(wxss, /\.exit-confirm-button--save\s*\{[^}]*background:\s*#002900/s, '确认框主按钮必须与静态项目保持深绿样式');

    console.log('doodle-editor.test.js: ALL PASS');
  } finally {
    Module._load = originalLoad;
    global.Component = originalComponent;
    global.wx = originalWx;
    delete require.cache[componentPath];
  }
}

run().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
