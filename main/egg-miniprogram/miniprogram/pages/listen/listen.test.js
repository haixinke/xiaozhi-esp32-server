const assert = require('assert');
const Module = require('module');

// Real Page, listen-session and listen-api; only platform I/O and transport vary.
const originalLoad = Module._load;
const originals = {
  wx: global.wx, Page: global.Page, getCurrentPages: global.getCurrentPages,
  setTimeout: global.setTimeout, clearTimeout: global.clearTimeout, now: Date.now
};
let definition;
let time;
let nextTimer;
let timers;
let metrics;
let drawResponse;
let submitResponse;
let feedbackResponse;
let navigateBackFails;
let ownerId;
const clone = (value) => value === undefined ? undefined : JSON.parse(JSON.stringify(value));
const event = (key) => ({ currentTarget: { dataset: { key } } });
const pendingText = '正在听你说…';
const fallbackText = '谢谢你愿意说给我听。';
function group(id = 1, count = 1) {
  return {
    groupId: id, groupName: `分组${id}`,
    questions: Array.from({ length: count }, (_, index) => ({
      id: id * 10 + index, text: `问题${index + 1}`,
      options: [{ key: 'A', text: '选项A' }, { key: 'E', text: '都不是' }]
    }))
  };
}
function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
async function flush() {
  for (let index = 0; index < 16; index += 1) await Promise.resolve();
}
async function advance(milliseconds) {
  const end = time + milliseconds;
  for (;;) {
    const next = [...timers.entries()].filter(([, timer]) => timer.at <= end)
      .sort((left, right) => left[1].at - right[1].at || left[0] - right[0])[0];
    if (!next) break;
    const [id, timer] = next;
    timers.delete(id);
    time = timer.at;
    timer.callback();
    await flush();
  }
  time = end;
  await flush();
}
function reset() {
  time = 10000;
  nextTimer = 0;
  timers = new Map();
  metrics = { draws: 0, submissions: [], feedbacks: [], setData: 0, createdTimers: 0,
    audioCreated: 0, audioPlayed: 0, audioDestroyed: 0, vibrations: 0, navigation: [] };
  drawResponse = async () => group();
  submitResponse = async () => ({ awardedStars: 5, rewardStatus: 'NEW_REWARD' });
  feedbackResponse = async () => ({ feedback: null });
  navigateBackFails = false;
  ownerId = 42;
}
Date.now = () => time;
global.setTimeout = (callback, delay = 0) => {
  const id = ++nextTimer;
  timers.set(id, { at: time + delay, callback });
  metrics.createdTimers += 1;
  return id;
};
global.clearTimeout = (id) => timers.delete(id);
global.Page = (value) => { definition = value; };
global.getCurrentPages = () => [{}, {}];
global.wx = {
  getSystemSetting: () => ({ reducedMotion: false }),
  createInnerAudioContext() {
    metrics.audioCreated += 1;
    return { play() { metrics.audioPlayed += 1; }, stop() {},
      destroy() { metrics.audioDestroyed += 1; }, onError() {} };
  },
  vibrateShort() { metrics.vibrations += 1; },
  navigateBack(options) {
    metrics.navigation.push('back');
    if (navigateBackFails) options.fail();
  },
  reLaunch({ url }) { metrics.navigation.push(url); }
};
const requestPath = require.resolve('../../utils/request');
const storePath = require.resolve('../../utils/pet-store');
const transport = {
  async get(url, data) {
    if (url === '/listen/draw') { metrics.draws += 1; return drawResponse(); }
    assert.strictEqual(url, '/listen/feedback');
    metrics.feedbacks.push(data.groupId);
    return feedbackResponse(data.groupId);
  },
  async post(url, data) {
    assert.strictEqual(url, '/listen/answers');
    metrics.submissions.push(clone(data));
    return submitResponse(data);
  }
};
Module._load = function (request, parent, isMain) {
  const target = Module._resolveFilename(request, parent);
  if (target === requestPath) return transport;
  if (target === storePath) return {
    getPet: () => ({ id: 'pet-a', name: '宝宝', prototype: '玉兔', ownerId: 42 }),
    getStage: () => 'hatched', getUser: () => ({ id: ownerId }), getIdentityId: () => ownerId
  };
  return originalLoad.call(this, request, parent, isMain);
};
require('./listen');
function makePage() {
  return {
    ...definition,
    data: clone(definition.data),
    setData(changes) { metrics.setData += 1; this.data = { ...this.data, ...clone(changes) }; }
  };
}
async function boot() {
  const page = makePage();
  page.onLoad();
  if (page.onShow) page.onShow();
  await flush();
  return page;
}
async function submit(page) {
  page.onSelect(event('A'));
  const result = page.onNext();
  await flush();
  return result;
}
const cases = [
  ['temporary hiding preserves the current question and filled answers', async () => {
    drawResponse = async () => group(1, 2);
    const page = await boot();
    page.onSelect(event('A'));
    await page.onNext();
    page.onSelect(event('E'));
    page.onSupplement({ detail: { value: '补充的小事' } });
    page.onHide();
    const hiddenWrites = metrics.setData;
    await advance(30000);
    assert.strictEqual(metrics.setData, hiddenWrites, 'hidden pages must not receive updates');
    assert.strictEqual(typeof page.onShow, 'function', 'temporary hiding requires a resume lifecycle');
    page.onShow();
    await flush();
    assert.strictEqual(metrics.draws, 1);
    assert.strictEqual(page.data.questionNumber, 2);
    assert.strictEqual(page.data.selected, 'E');
    assert.strictEqual(page.data.supplement, '补充的小事');
    await page.onNext();
    assert.deepStrictEqual(metrics.submissions[0].answers, [
      { questionId: 10, optionKey: 'A' },
      { questionId: 11, optionKey: 'E', customText: '补充的小事' }
    ]);
    page.onUnload();
  }],
  ['a submission completed while hidden resumes without rewards or another submission', async () => {
    const pending = deferred();
    submitResponse = () => pending.promise;
    const page = await boot();
    page.onSelect(event('A'));
    const submitting = page.onNext();
    await flush();
    page.onHide();
    const hidden = { writes: metrics.setData, timers: metrics.createdTimers };
    pending.resolve({ awardedStars: 5, rewardStatus: 'NEW_REWARD' });
    await submitting;
    await flush();
    assert.strictEqual(metrics.setData, hidden.writes, 'hidden submission completion must not call setData');
    assert.strictEqual(metrics.createdTimers, hidden.timers, 'hidden completion must not start reward or poll timers');
    assert.strictEqual(metrics.audioCreated, 0);
    page.onShow();
    await flush();
    assert.strictEqual(page.data.view, 'feedback');
    assert.strictEqual(page.data.awardedStars, 5);
    assert.strictEqual(page.data.awardVisible, false);
    assert.strictEqual(metrics.submissions.length, 1);
    await advance(1000);
    assert.strictEqual(metrics.audioCreated, 0);
    assert.strictEqual(metrics.vibrations, 0);
    page.onUnload();
  }],
  ['foreground reward plays once and hide cleans its presentation', async () => {
    const page = await boot();
    await submit(page);
    assert.strictEqual(page.data.awardVisible, true);
    await advance(900);
    assert.strictEqual(metrics.audioPlayed, 1);
    assert.strictEqual(metrics.vibrations, 1);
    page.onHide();
    assert.strictEqual(timers.size, 0, 'hide must stop reward and feedback timers');
    assert.strictEqual(metrics.audioDestroyed, 1);
    page.onShow();
    await advance(2500);
    assert.strictEqual(page.data.awardVisible, false);
    assert.strictEqual(metrics.audioPlayed, 1, 'resume must not replay the award');
    assert.strictEqual(metrics.vibrations, 1);
    page.onUnload();
  }],
  ['unload invalidates late draw, submit and feedback completions', async () => {
    for (const stage of ['draw', 'submit', 'feedback']) {
      reset();
      const pending = deferred();
      if (stage === 'draw') drawResponse = () => pending.promise;
      if (stage === 'submit') submitResponse = () => pending.promise;
      if (stage === 'feedback') feedbackResponse = () => pending.promise;
      const page = await boot();
      if (stage !== 'draw') { page.onSelect(event('A')); page.onNext(); await flush(); }
      page.onUnload();
      const disposed = { writes: metrics.setData, timers: metrics.createdTimers };
      const value = stage === 'draw' ? group() : stage === 'submit' ? { awardedStars: 5 } : { feedback: '晚到反馈' };
      pending.resolve(value);
      await flush();
      assert.strictEqual(metrics.setData, disposed.writes, `${stage} completion must not update an unloaded page`);
      assert.strictEqual(metrics.createdTimers, disposed.timers, `${stage} completion must not create timers after unload`);
      assert.strictEqual(timers.size, 0);
    }
  }],
  ['feedback has a five-second foreground budget and late text cannot replace fallback', async () => {
    const pending = deferred();
    feedbackResponse = () => pending.promise;
    const page = await boot();
    await submit(page);
    await advance(2000);
    page.onHide();
    const hiddenWrites = metrics.setData;
    await advance(600000);
    assert.strictEqual(metrics.setData, hiddenWrites);
    page.onShow();
    await advance(2999);
    assert.strictEqual(page.data.feedbackText, pendingText);
    await advance(1);
    assert.strictEqual(page.data.feedbackText, fallbackText, 'deadline must apply even when a request never returns');
    pending.resolve({ feedback: '来晚了' });
    await flush();
    assert.strictEqual(page.data.feedbackText, fallbackText, 'fallback is final for this group');
    assert.strictEqual(metrics.feedbacks.length, 1, 'resume must not overlap an in-flight feedback query');
    page.onUnload();
  }],
  ['continuing to another group discards the old feedback response', async () => {
    const oldFeedback = deferred();
    feedbackResponse = () => oldFeedback.promise;
    const page = await boot();
    await submit(page);
    drawResponse = async () => group(2);
    page.drawGroup();
    await flush();
    assert.strictEqual(page.data.groupName, '分组2');
    assert.strictEqual(page.data.view, 'question');
    const writes = metrics.setData;
    oldFeedback.resolve({ feedback: '旧分组反馈' });
    await flush();
    assert.strictEqual(page.data.view, 'question');
    assert.notStrictEqual(page.data.feedbackText, '旧分组反馈');
    assert.strictEqual(metrics.setData, writes);
    page.onUnload();
  }],
  ['failed submission retains answers for an explicit manual retry', async () => {
    submitResponse = async () => { throw { userMessage: '网络中断，请重试' }; };
    const page = await boot();
    page.onSelect(event('E'));
    page.onSupplement({ detail: { value: '保留这句' } });
    await page.onNext();
    assert.strictEqual(page.data.busy, false);
    assert.strictEqual(page.data.error, '网络中断，请重试');
    assert.strictEqual(page.data.selected, 'E');
    assert.strictEqual(page.data.supplement, '保留这句');
    await advance(10000);
    assert.strictEqual(metrics.submissions.length, 1, 'failure must not automatically retry');
    submitResponse = async () => ({ awardedStars: 0 });
    await page.onNext();
    assert.strictEqual(metrics.submissions.length, 2);
    assert.deepStrictEqual(metrics.submissions[1], metrics.submissions[0]);
    assert.strictEqual(page.data.view, 'feedback');
    page.onUnload();
  }],
  ['account change while hidden invalidates the previous answering session', async () => {
    const pending = deferred();
    submitResponse = () => pending.promise;
    const page = await boot();
    page.onSelect(event('A'));
    page.onNext();
    await flush();
    page.onHide();
    ownerId = 43;
    page.onShow();
    assert.strictEqual(page.data.view, 'blocked', 'another account must not resume the previous answers');
    const blocked = { writes: metrics.setData, timers: metrics.createdTimers };
    pending.resolve({ awardedStars: 5 });
    await flush();
    assert.strictEqual(page.data.view, 'blocked');
    assert.strictEqual(metrics.setData, blocked.writes);
    assert.strictEqual(metrics.createdTimers, blocked.timers);
    assert.strictEqual(metrics.audioCreated, 0);
    page.onUnload();
  }],
  ['return-to-room invalidates the session before navigation fallback', async () => {
    const pending = deferred();
    submitResponse = () => pending.promise;
    navigateBackFails = true;
    const page = await boot();
    page.onSelect(event('A'));
    page.onNext();
    await flush();
    page.onReturnRoom();
    assert.deepStrictEqual(metrics.navigation, ['back', '/pages/home/home']);
    const exiting = { writes: metrics.setData, timers: metrics.createdTimers };
    pending.resolve({ awardedStars: 5 });
    await flush();
    assert.strictEqual(metrics.setData, exiting.writes);
    assert.strictEqual(metrics.createdTimers, exiting.timers);
    assert.strictEqual(metrics.audioCreated, 0);
  }]
];
(async () => {
  let failed = 0;
  for (const [name, run] of cases) {
    reset();
    try { await run(); console.log(`PASS ${name}`); }
    catch (error) { failed += 1; console.error(`FAIL ${name}: ${error.message}`); }
  }
  assert.strictEqual(failed, 0, `${failed} listen page cases failed`);
  console.log('listen.test.js: ALL PASS');
})().catch((error) => { console.error(error); process.exitCode = 1; }).finally(() => {
  Module._load = originalLoad;
  Date.now = originals.now;
  for (const key of ['wx', 'Page', 'getCurrentPages', 'setTimeout', 'clearTimeout']) global[key] = originals[key];
});
