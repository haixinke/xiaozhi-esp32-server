const assert = require('assert');
const test = require('node:test');

const VoiceInputController = require('./voice-input-controller');

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

async function flush() {
  await Promise.resolve();
  await Promise.resolve();
}

class FakeAudio {
  constructor() {
    this.ready = true;
    this.usable = true;
    this.startResult = true;
    this.startCalls = 0;
    this.stopCalls = [];
    this.stop = null;
  }

  isReady() { return this.ready; }
  isUsable() { return this.usable; }
  startRecord() { this.startCalls += 1; return this.startResult; }
  stopRecord(options) {
    this.stopCalls.push(options);
    if (!this.stop) this.stop = deferred();
    return this.stop.promise;
  }
  resolveStop(result) {
    this.stop.resolve(Object.assign({ flushedFrames: 0, timedOut: false }, result));
  }
  rejectStop(error) { this.stop.reject(error || new Error('stop failed')); }
}

class FakeSocket {
  constructor() {
    this.generation = 1;
    this.calls = [];
    this.begin = null;
    this.frame = null;
    this.finish = null;
    this.abort = null;
  }

  getConnectionGeneration() { return this.generation; }
  beginVoiceTurn(id) {
    this.calls.push(['begin', id]);
    return this.begin ? this.begin.promise : Promise.resolve();
  }
  sendVoiceFrame(id, frame) {
    this.calls.push(['frame', id, frame]);
    return this.frame ? this.frame.promise : Promise.resolve();
  }
  finishVoiceTurn(id) {
    this.calls.push(['finish', id]);
    return this.finish ? this.finish.promise : Promise.resolve();
  }
  abortVoiceTurn(id) {
    this.calls.push(['abort', id]);
    return this.abort ? this.abort.promise : Promise.resolve();
  }
}

function create(options) {
  const audio = new FakeAudio();
  const socket = new FakeSocket();
  const states = [];
  const outcomes = [];
  const errors = [];
  const waiting = [];
  const controller = new VoiceInputController(Object.assign({
    audio,
    socket,
    ackTimeoutMs: 10,
    responseTimeoutMs: 15,
    onStateChange: (state) => states.push(state),
    onWaiting: (id) => waiting.push(id),
    onTerminal: (outcome) => outcomes.push(outcome),
    onUserError: (message) => errors.push(message),
  }, options));
  return { controller, audio, socket, states, outcomes, errors, waiting };
}

async function startRecording(ctx) {
  assert.strictEqual(ctx.controller.press(), true);
  ctx.controller.handleRecordStart();
  await flush();
  assert.strictEqual(ctx.controller.getState(), 'recording');
}

test('press gates pending, stub, and rejected recording engines', () => {
  const pending = create();
  pending.audio.ready = false;
  assert.strictEqual(pending.controller.press(), false);
  assert.match(pending.errors[0], /准备中/);

  const stub = create();
  stub.audio.usable = false;
  assert.strictEqual(stub.controller.press(), false);
  assert.match(stub.errors[0], /加载失败/);

  const rejected = create();
  rejected.audio.startResult = false;
  assert.strictEqual(rejected.controller.press(), false);
  assert.strictEqual(rejected.controller.getState(), 'idle');
  assert.deepStrictEqual(rejected.outcomes, ['error']);
});

test('late record start after an early release never creates a server turn', async () => {
  const ctx = create();
  assert.strictEqual(ctx.controller.press(), true);
  const release = ctx.controller.release();
  ctx.audio.resolveStop({ reason: 'released-before-start' });
  await release;
  ctx.controller.handleRecordStart();
  assert.deepStrictEqual(ctx.socket.calls, []);
  assert.strictEqual(ctx.controller.getState(), 'idle');
});

test('release sends begin, frames, a stop-tail frame, then finish after stop resolves', async () => {
  const ctx = create();
  await startRecording(ctx);
  const first = new ArrayBuffer(1);
  const tail = new ArrayBuffer(2);
  ctx.controller.handleAudioFrame(first);
  const release = ctx.controller.release();
  assert.deepStrictEqual(ctx.socket.calls.map((call) => call[0]), ['begin', 'frame']);
  assert.strictEqual(ctx.controller.getState(), 'stopping');
  ctx.controller.handleAudioFrame(tail);
  ctx.audio.resolveStop({ flushedFrames: 1 });
  await release;
  assert.deepStrictEqual(ctx.socket.calls.map((call) => call[0]), ['begin', 'frame', 'frame', 'finish']);
  assert.strictEqual(ctx.controller.getState(), 'waiting');
});

test('zero audio and stop timeout abort once without finish', async () => {
  const ctx = create();
  await startRecording(ctx);
  const release = ctx.controller.release();
  ctx.audio.resolveStop({ timedOut: true });
  await release;
  assert.deepStrictEqual(ctx.socket.calls.map((call) => call[0]), ['begin', 'abort']);
  assert.strictEqual(ctx.controller.getState(), 'idle');
  assert.deepStrictEqual(ctx.outcomes, ['no_audio']);
});

test('zero frames without a native timeout still aborts rather than finishing', async () => {
  const ctx = create();
  await startRecording(ctx);
  const release = ctx.controller.release();
  ctx.audio.resolveStop({ timedOut: false });
  await release;
  assert.deepStrictEqual(ctx.socket.calls.map((call) => call[0]), ['begin', 'abort']);
  assert.deepStrictEqual(ctx.outcomes, ['no_audio']);
});

test('slide cancellation flushes false and issues Abort at most once', async () => {
  const ctx = create();
  await startRecording(ctx);
  ctx.controller.setCancelled(true);
  const first = ctx.controller.release();
  const second = ctx.controller.cancel('hide');
  ctx.audio.resolveStop({});
  await Promise.all([first, second]);
  assert.strictEqual(ctx.audio.stopCalls[0].flush, false);
  assert.strictEqual(ctx.socket.calls.filter((call) => call[0] === 'abort').length, 1);
  assert.strictEqual(ctx.controller.getState(), 'idle');
});

test('cancellation arriving during a release suppresses Finish and sends one Abort', async () => {
  const ctx = create();
  await startRecording(ctx);
  ctx.controller.handleAudioFrame(new ArrayBuffer(1));
  const release = ctx.controller.release();
  const cancel = ctx.controller.cancel('hide');
  ctx.audio.resolveStop({});
  await Promise.all([release, cancel]);
  assert.deepStrictEqual(ctx.audio.stopCalls.map((call) => call.flush), [true, false]);
  assert.strictEqual(ctx.socket.calls.filter((call) => call[0] === 'finish').length, 0);
  assert.strictEqual(ctx.socket.calls.filter((call) => call[0] === 'abort').length, 1);
  assert.deepStrictEqual(ctx.outcomes, ['cancelled']);
});

test('record and encode failures abort a current turn exactly once', async () => {
  for (const scope of ['record', 'encode']) {
    const ctx = create();
    await startRecording(ctx);
    await ctx.controller.handleAudioFailure(scope);
    await ctx.controller.handleAudioFailure(scope);
    assert.strictEqual(ctx.socket.calls.filter((call) => call[0] === 'abort').length, 1);
    assert.strictEqual(ctx.controller.getState(), 'idle');
  }
});

test('native stop rejection and begin, frame, finish send failures converge to idle', async () => {
  const stopFailure = create();
  await startRecording(stopFailure);
  const release = stopFailure.controller.release();
  stopFailure.audio.rejectStop();
  await release;
  assert.strictEqual(stopFailure.controller.getState(), 'idle');

  const beginFailure = create();
  beginFailure.socket.begin = deferred();
  assert.strictEqual(beginFailure.controller.press(), true);
  beginFailure.controller.handleRecordStart();
  beginFailure.socket.begin.reject(new Error('begin'));
  await flush();
  assert.strictEqual(beginFailure.controller.getState(), 'idle');

  const frameFailure = create();
  frameFailure.socket.frame = deferred();
  await startRecording(frameFailure);
  frameFailure.controller.handleAudioFrame(new ArrayBuffer(1));
  frameFailure.socket.frame.reject(new Error('frame'));
  await flush();
  assert.strictEqual(frameFailure.controller.getState(), 'idle');

  const finishFailure = create();
  finishFailure.socket.finish = deferred();
  await startRecording(finishFailure);
  finishFailure.controller.handleAudioFrame(new ArrayBuffer(1));
  const failedRelease = finishFailure.controller.release();
  finishFailure.audio.resolveStop({});
  await flush();
  finishFailure.socket.finish.reject(new Error('finish'));
  await failedRelease;
  assert.strictEqual(finishFailure.controller.getState(), 'idle');
  assert.strictEqual(finishFailure.socket.calls.filter((call) => call[0] === 'abort').length, 0);
});

test('a delayed send failure during release cannot add a second terminal action', async () => {
  const ctx = create();
  ctx.socket.frame = deferred();
  await startRecording(ctx);
  ctx.controller.handleAudioFrame(new ArrayBuffer(1));
  const release = ctx.controller.release();
  ctx.audio.resolveStop({});
  await flush();
  assert.deepStrictEqual(ctx.socket.calls.map((call) => call[0]), ['begin', 'frame', 'finish']);
  ctx.socket.frame.reject(new Error('late frame failure'));
  await release;
  await flush();
  assert.strictEqual(ctx.controller.getState(), 'idle');
  assert.strictEqual(ctx.socket.calls.filter((call) => call[0] === 'finish').length, 1);
  assert.strictEqual(ctx.socket.calls.filter((call) => call[0] === 'abort').length, 0);
});

test('stopped and terminal feedback received while finish is pending are retained', async () => {
  const ctx = create();
  ctx.socket.finish = deferred();
  await startRecording(ctx);
  ctx.controller.handleAudioFrame(new ArrayBuffer(1));
  const release = ctx.controller.release();
  ctx.audio.resolveStop({});
  await flush();
  ctx.controller.handleMessage({ type: 'listen', state: 'stopped', turnId: 'm-1' });
  ctx.controller.handleMessage({ type: 'listen', state: 'no_speech', turnId: 'm-1' });
  ctx.socket.finish.resolve();
  await release;
  assert.strictEqual(ctx.controller.getState(), 'idle');
  assert.deepStrictEqual(ctx.outcomes, ['no_speech']);
});

test('early stopped starts the response timer after finish, never the ack timer', async () => {
  const ctx = create({ responseTimeoutMs: 5 });
  ctx.socket.finish = deferred();
  await startRecording(ctx);
  ctx.controller.handleAudioFrame(new ArrayBuffer(1));
  const release = ctx.controller.release();
  ctx.audio.resolveStop({});
  await flush();
  ctx.controller.handleMessage({ type: 'listen', state: 'stopped', turnId: 'm-1' });
  ctx.socket.finish.resolve();
  await release;
  await new Promise((resolve) => setTimeout(resolve, 8));
  assert.strictEqual(ctx.controller.getState(), 'idle');
  assert.deepStrictEqual(ctx.outcomes, ['response_timeout']);
});

test('ack and response timeouts fail independently and terminal listen messages close waiting turns', async () => {
  const ack = create({ ackTimeoutMs: 5 });
  await startRecording(ack);
  ack.controller.handleAudioFrame(new ArrayBuffer(1));
  const release = ack.controller.release();
  ack.audio.resolveStop({});
  await release;
  await new Promise((resolve) => setTimeout(resolve, 8));
  assert.deepStrictEqual(ack.outcomes, ['ack_timeout']);

  for (const state of ['error', 'cancelled']) {
    const ctx = create();
    await startRecording(ctx);
    ctx.controller.handleAudioFrame(new ArrayBuffer(1));
    const done = ctx.controller.release();
    ctx.audio.resolveStop({});
    await done;
    ctx.controller.handleMessage({ type: 'listen', state, turnId: 'm-1' });
    assert.deepStrictEqual(ctx.outcomes, [state]);
  }
});

test('STT/TTS starts recognize current waiting turn, while stale callbacks cannot touch a new turn', async () => {
  const ctx = create();
  await startRecording(ctx);
  ctx.controller.handleAudioFrame(new ArrayBuffer(1));
  const firstRelease = ctx.controller.release();
  ctx.audio.resolveStop({});
  await firstRelease;
  ctx.controller.handleMessage({ type: 'stt', text: 'heard' });
  assert.deepStrictEqual(ctx.outcomes, ['recognized']);

  await startRecording(ctx);
  ctx.controller.handleMessage({ type: 'listen', state: 'error', turnId: 'm-1' });
  assert.strictEqual(ctx.controller.getState(), 'recording');
  ctx.socket.generation = 2;
  ctx.controller.handleMessage({ type: 'tts', state: 'start' });
  assert.strictEqual(ctx.controller.getState(), 'recording');
});

test('a matching TTS start recognizes a waiting turn and a stale async callback cannot end its successor', async () => {
  const ctx = create();
  ctx.socket.begin = deferred();
  assert.strictEqual(ctx.controller.press(), true);
  ctx.controller.handleRecordStart();
  await ctx.controller.handleAudioFailure('record');
  ctx.socket.begin.reject(new Error('old begin'));
  await flush();

  ctx.socket.begin = null;
  await startRecording(ctx);
  ctx.controller.handleAudioFrame(new ArrayBuffer(1));
  const release = ctx.controller.release();
  ctx.audio.resolveStop({});
  await release;
  ctx.controller.handleMessage({ type: 'tts', state: 'start' });
  assert.deepStrictEqual(ctx.outcomes, ['error', 'recognized']);
  assert.strictEqual(ctx.controller.getState(), 'idle');
});

test('generation changes, disconnects, repeated lifecycle cancellation, and destroy are idempotent', async () => {
  const generation = create();
  await startRecording(generation);
  generation.controller.handleSocketState('connected', 2);
  await flush();
  assert.strictEqual(generation.controller.getState(), 'idle');

  const lifecycle = create();
  await startRecording(lifecycle);
  const hide = lifecycle.controller.cancel('hide');
  const unload = lifecycle.controller.cancel('unload');
  lifecycle.audio.resolveStop({});
  await Promise.all([hide, unload]);
  lifecycle.controller.destroy();
  lifecycle.controller.destroy();
  assert.strictEqual(lifecycle.socket.calls.filter((call) => call[0] === 'abort').length, 1);
  assert.strictEqual(lifecycle.controller.press(), false);
});

test('best-effort Abort rejection is consumed without changing the terminal result', async () => {
  const ctx = create();
  ctx.socket.abort = deferred();
  await startRecording(ctx);
  const release = ctx.controller.release();
  ctx.audio.resolveStop({});
  await release;
  ctx.socket.abort.reject(new Error('abort send failed'));
  await flush();
  assert.deepStrictEqual(ctx.outcomes, ['no_audio']);
  assert.strictEqual(ctx.controller.getState(), 'idle');
});
