// 会话边界测试：隐藏暂停、网络重试及完成响应不能导致额外奖扣。
const assert = require('node:assert/strict');
const { createDrawingSession } = require('./drawing-session');

(async () => {
  let now = 0;
  let callback;
  let startCalls = [];
  let attempts = 0;
  let resolveCompletion;
  const completionCalls = [];
  const api = {
    config: async () => ({ consumeStars: 3, earnStars: 0 }),
    start: async body => {
      startCalls = startCalls.concat(body);
      if (++attempts === 1) throw new Error('network');
      return { sessionId: 's1', referenceId: 'short_pencil' };
    },
    complete: (...args) => {
      completionCalls.push(args);
      return new Promise(resolve => { resolveCompletion = resolve; });
    }
  };
  const session = createDrawingSession({ api, petId: 'p1', requestId: 'same-request',
    now: () => now, setTimer: fn => { callback = fn; return 1; }, clearTimer() {} });
  await session.load();
  await assert.rejects(session.start());
  await session.start();
  assert.equal(startCalls[0].requestId, startCalls[1].requestId);
  assert.deepEqual(startCalls[0], { petId: 'p1', requestId: 'same-request' },
    '开局只提交宠物门槛与幂等标识，不确认或承诺金额');
  session.beginPreview();
  now = 2000;
  session.pause();
  assert.equal(session.snapshot().peekRemainingMs, 3000);
  now = 50000;
  session.resume();
  assert.equal(session.snapshot().peekVisible, true);
  callback();
  assert.equal(session.snapshot().peekVisible, false);
  for (let i = 0; i < 3; i += 1) { assert.equal(session.peekAgain(), true); session.dismissPeek(); }
  assert.equal(session.peekAgain(), false);
  const pending = session.complete();
  assert.deepEqual(completionCalls, [['s1']], '完成仅提交局次身份，不发送笔画、坐标或作画时间');
  session.pause();
  resolveCompletion({ awardedStars: 0, sessionId: 's1' });
  await pending;
  assert.equal(session.snapshot().result.sessionId, 's1', '隐藏期间完成响应仍保存');
  assert.equal(session.snapshot().animateReward, false, '隐藏期间完成不补播奖励');
  session.resume();
  assert.equal(session.snapshot().animateReward, false);
  assert.equal(session.peekAgain(), false);
  session.dispose();
  console.log('drawing-session tests passed');
})().catch(error => { console.error(error); process.exitCode = 1; });
