const assert = require('assert');
const { createListenSession, FEEDBACK_PENDING_TEXT, FEEDBACK_FALLBACK_TEXT } = require('./listen-session');

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
function fakeClock() {
  let time = 0;
  let sequence = 0;
  const timers = new Map();
  return {
    now: () => time,
    setTimeout(fn, delay) { const id = ++sequence; timers.set(id, { at: time + delay, fn }); return id; },
    clearTimeout(id) { timers.delete(id); },
    advance(ms) {
      const end = time + ms;
      while (true) {
        const next = [...timers].filter(([, timer]) => timer.at <= end).sort((a, b) => a[1].at - b[1].at)[0];
        if (!next) break;
        timers.delete(next[0]);
        time = next[1].at;
        next[1].fn();
      }
      time = end;
    },
    pending: () => timers.size
  };
}
const group = (id = 'g1') => ({ groupId: id, groupName: id, questions: [
  { id: `${id}-q1`, text: '今天怎么样？', options: [{ key: 'A', text: '很好' }, { key: 'E', text: '都不是' }] }
] });
async function flush() { for (let index = 0; index < 8; index += 1) await Promise.resolve(); }
function setup(overrides = {}) {
  const clock = fakeClock();
  const changes = [];
  const awards = [];
  const calls = { draw: 0, submit: [], feedback: [] };
  const api = {
    draw() { calls.draw += 1; return Promise.resolve(group()); },
    submitAnswers(...args) { calls.submit.push(args); return Promise.resolve({ awardedStars: 5 }); },
    getFeedback(id) { calls.feedback.push(id); return new Promise(() => {}); },
    ...overrides
  };
  const session = createListenSession({ api, clock, onChange: (state) => changes.push(state), onAward: (stars) => awards.push(stars) });
  return { session, clock, changes, awards, calls, api };
}
async function question(context) {
  context.session.resume();
  await context.session.drawGroup();
  context.session.select('A');
}

async function run() {
  const pending = deferred();
  const context = setup({ submitAnswers: () => pending.promise });
  await question(context);
  const submitting = context.session.next();
  context.session.pause();
  const count = context.changes.length;
  pending.resolve({ awardedStars: 5 });
  await submitting;
  assert.strictEqual(context.session.getSnapshot().view, 'feedback');
  assert.strictEqual(context.changes.length, count, 'hidden submission must not emit presentation changes');
  assert.deepStrictEqual(context.awards, [], 'hidden success must not award or queue a presentation');
  assert.strictEqual(context.clock.pending(), 0, 'hidden success must not start feedback timers');
  context.session.resume();
  assert.strictEqual(context.changes.at(-1).awardedStars, 5);
  assert.deepStrictEqual(context.awards, [], 'resume must not replay the award');
  context.session.dispose();
  {
    const context = setup();
    await question(context);
    context.session.supplement('保留我的答案');
    context.session.dispose();
    assert.strictEqual(context.session.getSnapshot().supplement, '', 'unload discards unsubmitted answer data');
    context.session.resume();
    assert.strictEqual(context.clock.pending(), 0);
  }
  {
    const pending = deferred();
    const context = setup({ getFeedback: () => { context.calls.feedback.push('g1'); return pending.promise; } });
    await question(context);
    await context.session.next();
    context.clock.advance(2000);
    context.session.pause();
    const count = context.changes.length;
    assert.strictEqual(context.clock.pending(), 0, 'pause removes deadline and poll timers');
    context.clock.advance(60000);
    assert.strictEqual(context.session.getSnapshot().feedbackText, FEEDBACK_PENDING_TEXT);
    assert.strictEqual(context.changes.length, count);
    context.session.resume();
    assert.strictEqual(context.calls.feedback.length, 1, 'resume cannot overlap a still-pending query');
    context.clock.advance(2999);
    assert.strictEqual(context.session.getSnapshot().feedbackText, FEEDBACK_PENDING_TEXT);
    context.clock.advance(1);
    assert.strictEqual(context.session.getSnapshot().feedbackText, FEEDBACK_FALLBACK_TEXT,
      'five seconds of foreground time must end even a hanging network request');
    pending.resolve({ feedback: 'too late' });
    await flush();
    assert.strictEqual(context.session.getSnapshot().feedbackText, FEEDBACK_FALLBACK_TEXT);
    context.session.pause();
    context.session.resume();
    assert.strictEqual(context.calls.feedback.length, 1, 'fallback is final, including after later resumes');
    context.session.dispose();
  }
  {
    const pending = deferred();
    const context = setup({ getFeedback: () => pending.promise });
    await question(context);
    await context.session.next();
    context.session.pause();
    const count = context.changes.length;
    pending.resolve({ feedback: ' 已经准备好 ' });
    await flush();
    assert.strictEqual(context.changes.length, count, 'a hidden ready response is cached without rendering');
    assert.strictEqual(context.clock.pending(), 0);
    context.session.resume();
    assert.strictEqual(context.session.getSnapshot().feedbackText, '已经准备好');
    assert.strictEqual(context.clock.pending(), 0);
    context.session.dispose();
  }
  {
    const context = setup();
    let queries = 0;
    context.api.getFeedback = () => {
      queries += 1;
      if (queries === 1) return Promise.resolve({ feedback: null });
      if (queries === 2) return Promise.reject(new Error('offline'));
      return Promise.resolve({ feedback: '第三次就绪' });
    };
    await question(context);
    await context.session.next();
    await flush();
    assert.strictEqual(queries, 1);
    context.clock.advance(499);
    assert.strictEqual(queries, 1);
    context.clock.advance(1);
    await flush();
    assert.strictEqual(queries, 2);
    context.clock.advance(500);
    await flush();
    assert.strictEqual(context.session.getSnapshot().feedbackText, '第三次就绪');
    assert.deepStrictEqual(context.awards, [5]);
    assert.strictEqual(context.clock.pending(), 0);
    context.session.dispose();
  }
  {
    const pending = deferred();
    const context = setup();
    let submits = 0;
    context.api.submitAnswers = (id, answers) => {
      submits += 1;
      context.calls.submit.push([id, answers]);
      return submits === 1 ? pending.promise : Promise.resolve({ awardedStars: 0 });
    };
    await question(context);
    context.session.supplement('我的补充');
    const first = context.session.next();
    await context.session.next();
    context.session.select('E');
    context.session.supplement('不能覆盖');
    context.session.addNote();
    assert.strictEqual(submits, 1, 'busy next cannot double-submit');
    context.session.pause();
    pending.reject({ userMessage: '暂时无法保存' });
    await first;
    assert.strictEqual(context.session.getSnapshot().selected, 'A');
    context.session.resume();
    assert.strictEqual(context.session.getSnapshot().error, '暂时无法保存');
    assert.strictEqual(context.session.getSnapshot().supplement, '我的补充');
    assert.strictEqual(submits, 1, 'resume never retries submission automatically');
    await context.session.next();
    assert.deepStrictEqual(context.calls.submit[1], ['g1', [{ questionId: 'g1-q1', optionKey: 'A', customText: '我的补充' }]]);
    assert.deepStrictEqual(context.awards, []);
    context.session.dispose();
  }
  {
    const context = setup({ draw: async () => ({ ...group(), questions: [...group().questions, ...group('second').questions] }) });
    context.session.select('A');
    await context.session.next();
    context.session.supplement('ignored');
    context.session.addNote();
    await question(context);
    const external = context.session.getSnapshot();
    external.question.options[0].key = 'malicious';
    context.session.select('malicious');
    assert.strictEqual(context.session.getSnapshot().selected, 'A', 'external snapshots cannot mutate session state');
    await context.session.next();
    assert.strictEqual(context.session.getSnapshot().questionNumber, 2);
    assert.strictEqual(context.session.getSnapshot().progress, 50);
    assert.strictEqual(context.calls.submit.length, 0, 'only the last question submits');
    context.session.select('E');
    assert.strictEqual(context.session.getSnapshot().noteOpen, true);
    context.session.supplement('字'.repeat(205));
    assert.strictEqual(context.session.getSnapshot().supplement.length, 200);
    context.session.addNote();
    await context.session.next();
    assert.strictEqual(context.calls.submit[0][1].length, 2);
    assert.strictEqual(context.calls.submit[0][1][0].customText, undefined);
    context.session.dispose();
  }
  {
    const old = deferred();
    const context = setup({ getFeedback: () => old.promise });
    await question(context);
    await context.session.next();
    context.api.draw = async () => group('g2');
    await context.session.drawGroup();
    old.resolve({ feedback: '第一组的旧反馈' });
    await flush();
    assert.strictEqual(context.session.getSnapshot().groupName, 'g2');
    assert.strictEqual(context.session.getSnapshot().view, 'question');
    assert.strictEqual(context.session.getSnapshot().feedbackText, FEEDBACK_PENDING_TEXT);
    assert.strictEqual(context.clock.pending(), 0, 'old group response cannot restart polling');
    context.session.dispose();
  }
  for (const [error, view, title] of [
    [{ code: 10382 }, 'end', '这些小事都听你说过啦'],
    [{ code: 10384 }, 'end', '明天再来'],
    [{ userMessage: '无法获取题目' }, 'blocked', ''],
    [null, 'blocked', '']
  ]) {
    const context = setup({ draw: () => Promise.reject(error) });
    context.session.resume();
    await context.session.drawGroup();
    assert.strictEqual(context.session.getSnapshot().view, view);
    assert.strictEqual(context.session.getSnapshot().endTitle, title);
    if (view === 'blocked') assert.ok(context.session.getSnapshot().error);
    context.session.dispose();
  }
  for (const empty of [null, {}, { questions: [] }]) {
    const context = setup({ draw: async () => empty });
    context.session.resume();
    await context.session.drawGroup();
    assert.strictEqual(context.session.getSnapshot().view, 'end');
    context.session.dispose();
  }
  {
    const draw = deferred();
    let drawCount = 0;
    const context = setup({ draw: () => { drawCount += 1; return draw.promise; } });
    context.session.resume();
    const first = context.session.drawGroup();
    await context.session.drawGroup();
    assert.strictEqual(drawCount, 1, 'repeated draw while loading cannot overlap');
    context.session.pause();
    const count = context.changes.length;
    draw.resolve(group());
    await first;
    assert.strictEqual(context.changes.length, count);
    context.session.resume();
    assert.strictEqual(context.session.getSnapshot().view, 'question');
    assert.strictEqual(drawCount, 1, 'resume uses the cached draw result');
    context.session.dispose();
  }
  for (const reject of [false, true]) {
    const pending = deferred();
    const context = setup({ draw: () => pending.promise });
    context.session.resume();
    const work = context.session.drawGroup();
    context.session.dispose();
    const count = context.changes.length;
    if (reject) pending.reject(new Error('late failure')); else pending.resolve(group());
    await work;
    context.session.pause();
    context.session.resume();
    await context.session.drawGroup();
    assert.strictEqual(context.changes.length, count);
    assert.strictEqual(context.clock.pending(), 0);
  }
  for (const reject of [false, true]) {
    const pending = deferred();
    const context = setup({ submitAnswers: () => pending.promise });
    await question(context);
    const work = context.session.next();
    context.session.dispose();
    const count = context.changes.length;
    if (reject) pending.reject(new Error('late failure')); else pending.resolve({ awardedStars: 10 });
    await work;
    assert.strictEqual(context.changes.length, count);
    assert.deepStrictEqual(context.awards, []);
    assert.strictEqual(context.clock.pending(), 0);
  }
  {
    const context = setup({ submitAnswers: async () => null, getFeedback: () => { throw new Error('offline'); } });
    await question(context);
    await context.session.next();
    await flush();
    context.session.pause();
    context.clock.advance(500000);
    context.session.resume();
    context.clock.advance(5000);
    await flush();
    assert.strictEqual(context.session.getSnapshot().awardedStars, 0);
    assert.strictEqual(context.session.getSnapshot().feedbackText, FEEDBACK_FALLBACK_TEXT);
    context.session.dispose();
  }
  console.log('listen-session.test.js: ALL PASS');
}
run().catch((error) => { console.error(error); process.exitCode = 1; });
