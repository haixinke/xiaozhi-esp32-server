const assert = require('assert');
const WebSocketManager = require('./websocket');

function makeSocketTask() {
  const handlers = {};
  return {
    sent: [],
    closeCalls: [],
    onOpen(callback) { handlers.open = callback; },
    onMessage(callback) { handlers.message = callback; },
    onError(callback) { handlers.error = callback; },
    onClose(callback) { handlers.close = callback; },
    send(options) { this.sent.push(options); },
    close(options) { this.closeCalls.push(options); },
    succeed(index) { this.sent[index].success(); },
    fail(index, err) { this.sent[index].fail(err || { errMsg: 'network down' }); },
    payloads() {
      return this.sent.map((entry) => {
        if (typeof entry.data !== 'string') return entry.data;
        try { return JSON.parse(entry.data); } catch (_) { return entry.data; }
      });
    },
    triggerOpen() { handlers.open(); },
    triggerMessage(data) { handlers.message({ data }); },
    triggerError(err) { handlers.error(err || { errMsg: 'socket failed' }); },
    triggerClose(res) { handlers.close(res || {}); },
  };
}

function connectedManager(task, options) {
  const ws = new WebSocketManager(options);
  ws.socket = task;
  ws.state = 'connected';
  return ws;
}

async function rejects(promise, pattern) {
  await assert.rejects(promise, pattern);
}

async function rejectsPromptly(promise, pattern) {
  let timeout;
  try {
    await Promise.race([
      assert.rejects(promise, pattern),
      new Promise((_, reject) => {
        timeout = setTimeout(() => reject(new Error('expected prompt rejection')), 30);
      }),
    ]);
  } finally {
    clearTimeout(timeout);
  }
}

async function testSerialVoiceSends() {
  const task = makeSocketTask();
  const frame = new ArrayBuffer(3);
  const ws = connectedManager(task);

  const start = ws.beginVoiceTurn('m-1');
  const audio = ws.sendVoiceFrame('m-1', frame);
  const end = ws.finishVoiceTurn('m-1');
  assert.strictEqual(task.sent.length, 1, 'only Start may be in flight');

  task.succeed(0);
  await start;
  assert.strictEqual(task.sent.length, 2, 'Audio waits for Start success');

  task.succeed(1);
  await audio;
  assert.strictEqual(task.sent.length, 3, 'End waits for tail Audio success');

  task.succeed(2);
  await end;
  assert.deepStrictEqual(task.payloads(), [
    { type: 'listen', mode: 'manual', state: 'start', turn_id: 'm-1' },
    frame,
    { type: 'listen', mode: 'manual', state: 'stop', turn_id: 'm-1' },
  ]);
}

async function testFailureIsStickyAndAbortIsDirect() {
  const task = makeSocketTask();
  const ws = connectedManager(task);
  const start = ws.beginVoiceTurn('m-2');
  const audio = ws.sendVoiceFrame('m-2', new ArrayBuffer(1));
  const end = ws.finishVoiceTurn('m-2');
  audio.catch(() => {});
  end.catch(() => {});

  task.succeed(0);
  await start;
  task.fail(1, { errMsg: 'write failed' });

  const abort = ws.abortVoiceTurn('m-2');
  assert.strictEqual(task.sent.length, 3, 'Abort bypasses the rejected serial tail');
  assert.deepStrictEqual(task.payloads()[2], { type: 'abort', turn_id: 'm-2' });
  task.succeed(2);

  await rejects(audio, /voice send fail: write failed/);
  await rejects(end, /voice send fail: write failed/);
  assert.strictEqual(task.sent.length, 3, 'End must not follow failed Audio');
  await abort;
}

async function testTeardownRejectsQueuedWorkWithoutReplay() {
  const oldTask = makeSocketTask();
  const newTask = makeSocketTask();
  const ws = connectedManager(oldTask);
  ws._bindSocketHandlers(oldTask, ws.getConnectionGeneration());
  const start = ws.beginVoiceTurn('m-3');
  const audio = ws.sendVoiceFrame('m-3', new ArrayBuffer(2));
  const end = ws.finishVoiceTurn('m-3');
  start.catch(() => {});
  audio.catch(() => {});
  end.catch(() => {});

  ws._teardownSocket(false);
  ws.socket = newTask;
  ws.state = 'connected';
  ws._connectionGeneration += 1;
  ws._bindSocketHandlers(newTask, ws.getConnectionGeneration());

  await rejectsPromptly(start, /stale voice socket generation/);
  await rejectsPromptly(audio, /stale voice socket generation/);
  await rejectsPromptly(end, /stale voice socket generation/);
  assert.strictEqual(oldTask.sent.length, 1, 'queued packets stay off the old task');
  assert.strictEqual(newTask.sent.length, 0, 'queued packets must not replay on a new task');

  oldTask.succeed(0);

  oldTask.triggerClose();
  assert.strictEqual(ws.socket, newTask, 'stale close must not clear the replacement task');
  assert.strictEqual(ws.state, 'connected', 'stale close must not change replacement state');
}

async function testInvalidationRejectsInflightAndQueuedWorkPromptly() {
  const oldTask = makeSocketTask();
  const replacementTask = makeSocketTask();
  const ws = connectedManager(oldTask);
  const start = ws.beginVoiceTurn('m-inflight');
  const frame = ws.sendVoiceFrame('m-inflight', new ArrayBuffer(1));

  ws._teardownSocket(false);
  ws.socket = replacementTask;
  ws.state = 'connected';
  ws._connectionGeneration += 1;

  await rejectsPromptly(start, /stale voice socket generation/);
  await rejectsPromptly(frame, /stale voice socket generation/);
  assert.strictEqual(replacementTask.sent.length, 0, 'invalidated work must not replay');

  oldTask.succeed(0);
  oldTask.fail(0, { errMsg: 'late failure' });
  assert.strictEqual(replacementTask.sent.length, 0, 'late callbacks must be inert');
}

async function testCurrentCloseRejectsInflightAndQueuedWorkPromptly() {
  const task = makeSocketTask();
  const ws = connectedManager(task);
  ws._bindSocketHandlers(task, ws.getConnectionGeneration());
  const start = ws.beginVoiceTurn('m-close');
  const frame = ws.sendVoiceFrame('m-close', new ArrayBuffer(1));

  task.triggerClose();
  await rejectsPromptly(start, /stale voice socket generation/);
  await rejectsPromptly(frame, /stale voice socket generation/);
  assert.strictEqual(ws.socket, null);
  task.succeed(0);
  task.fail(0, { errMsg: 'late failure' });
}

async function testAbortDoesNotFollowAnInflightEnd() {
  const task = makeSocketTask();
  const ws = connectedManager(task);
  const start = ws.beginVoiceTurn('m-end-inflight');
  task.succeed(0);
  await start;

  const end = ws.finishVoiceTurn('m-end-inflight');
  await Promise.resolve();
  assert.strictEqual(task.sent.length, 2, 'End must be in flight');
  await rejects(ws.abortVoiceTurn('m-end-inflight'), /voice turn end already in flight/);
  assert.strictEqual(task.sent.length, 2, 'Abort must not follow an in-flight End');

  task.succeed(1);
  await end;
  assert.deepStrictEqual(task.payloads().slice(-1), [
    { type: 'listen', mode: 'manual', state: 'stop', turn_id: 'm-end-inflight' },
  ]);
}

async function testAbortCancelsAnEndQueuedBehindAudio() {
  const task = makeSocketTask();
  const ws = connectedManager(task);
  const start = ws.beginVoiceTurn('m-end-queued');
  task.succeed(0);
  await start;

  const audio = ws.sendVoiceFrame('m-end-queued', new ArrayBuffer(1));
  const end = ws.finishVoiceTurn('m-end-queued');
  end.catch(() => {});
  await Promise.resolve();
  assert.strictEqual(task.sent.length, 2, 'Audio is the only in-flight send before Abort');

  const abort = ws.abortVoiceTurn('m-end-queued');
  assert.strictEqual(task.sent.length, 3, 'Abort replaces queued End as the sole terminal packet');
  assert.deepStrictEqual(task.payloads()[2], { type: 'abort', turn_id: 'm-end-queued' });
  task.succeed(1);
  task.succeed(2);
  await audio;
  await rejects(end, /voice turn terminal cancelled/);
  await abort;
  assert.strictEqual(task.sent.length, 3, 'cancelled End must never be invoked');
}

async function testListenFeedbackAndLegacyImmediateSends() {
  const messages = [];
  const task = makeSocketTask();
  const ws = connectedManager(task, { onMessage: (message) => messages.push(message) });

  ws._handleMessage({ data: JSON.stringify({ type: 'listen', state: 'stopped', turn_id: 'm-1' }) });
  assert.deepStrictEqual(messages, [{
    type: 'listen', state: 'stopped', turnId: 'm-1', reason: '',
    raw: { type: 'listen', state: 'stopped', turn_id: 'm-1' },
  }]);

  assert.strictEqual(ws.sendListenStart('auto'), true);
  assert.strictEqual(ws.sendListenStop(), true);
  assert.strictEqual(ws.sendAudioFrame(new ArrayBuffer(1)), true);
  assert.strictEqual(ws.sendAbort(), true);
  assert.deepStrictEqual(task.payloads(), [
    { type: 'listen', mode: 'auto', state: 'start' },
    { type: 'listen', mode: 'manual', state: 'stop' },
    task.sent[2].data,
    { type: 'abort' },
  ]);
}

async function testConnectionGenerationsIgnoreStaleHandlers() {
  const first = makeSocketTask();
  const second = makeSocketTask();
  const tasks = [first, second];
  global.wx = {
    connectSocket() { return tasks.shift(); },
  };
  const states = [];
  const ws = new WebSocketManager({ onStateChange: (state) => states.push(state) });

  ws.connect('ws://example.test/v1', 'device');
  assert.strictEqual(ws.getConnectionGeneration(), 1);
  ws.connect('ws://example.test/v1', 'device');
  assert.strictEqual(ws.getConnectionGeneration(), 2);
  first.triggerOpen();
  first.triggerMessage(JSON.stringify({ type: 'hello', session_id: 'old' }));
  first.triggerError({ errMsg: 'old' });
  first.triggerClose();
  assert.strictEqual(ws.socket, second);
  assert.strictEqual(ws.state, 'connecting');
  assert.deepStrictEqual(states, ['connecting']);
  second.triggerOpen();
  assert.deepStrictEqual(second.payloads()[0], {
    type: 'hello', version: 1, transport: 'websocket',
    audio_params: { format: 'opus', sample_rate: 24000, channels: 1, frame_duration: 60 },
  });
  second.triggerMessage(JSON.stringify({ type: 'hello', session_id: 'new' }));
  assert.strictEqual(ws.state, 'connected');
  assert.strictEqual(ws.sessionId, 'new');
  ws.disconnect();
}

async function testProtocolDispatchAndFailurePaths() {
  const messages = [];
  const errors = [];
  const task = makeSocketTask();
  const ws = connectedManager(task, {
    onMessage: (message) => messages.push(message),
    onError: (error, scope) => errors.push({ message: error.message, scope }),
  });
  const listenerStates = [];
  const listener = (state) => listenerStates.push(state);
  ws.onStateChange(listener);
  ws.onStateChange('not-a-function');
  ws.offStateChange(listener);
  ws.onStateChange(listener);

  const binary = new ArrayBuffer(1);
  ws._handleMessage({ data: binary });
  ws._handleMessage({ data: 'not json' });
  ws._handleMessage({ data: JSON.stringify({ type: 'stt', text: 'heard' }) });
  ws._handleMessage({ data: JSON.stringify({ type: 'tts', state: 'start', text: 'spoken' }) });
  ws._handleMessage({ data: JSON.stringify({ type: 'llm', text: 'answer', emotion: 'happy' }) });
  ws._handleMessage({ data: JSON.stringify({ type: 'goodbye' }) });
  ws._handleMessage({ data: JSON.stringify({ type: 'other' }) });
  ws._handleMessage({ data: 'null' });
  ws._handleMessage(null);
  assert.deepStrictEqual(messages.map((message) => message.type),
    ['audio', 'stt', 'tts', 'llm', 'goodbye', 'other']);
  assert.strictEqual(errors[0].scope, 'parse');

  assert.strictEqual(ws.sendHello(), true);
  assert.strictEqual(ws.sendPing(), true);
  assert.strictEqual(ws.sendText('text input'), true);
  assert.deepStrictEqual(task.payloads().slice(-3), [
    { type: 'hello', version: 1, transport: 'websocket',
      audio_params: { format: 'opus', sample_rate: 24000, channels: 1, frame_duration: 60 } },
    { type: 'ping' },
    { type: 'listen', mode: 'manual', state: 'detect', text: 'text input' },
  ]);

  ws._setState('disconnected');
  assert.strictEqual(ws.send({ type: 'ignored' }), false);
  assert.strictEqual(ws.sendAudioFrame(null), false);
  await rejects(ws.beginVoiceTurn('missing'), /voice socket unavailable/);
  await rejects(ws.sendVoiceFrame('missing', new ArrayBuffer(1)), /voice turn unavailable/);
  await rejects(ws.finishVoiceTurn('missing'), /voice turn unavailable/);
  await rejects(ws.abortVoiceTurn('missing'), /voice turn unavailable/);

  const throwingTask = makeSocketTask();
  throwingTask.send = () => { throw new Error('send blew up'); };
  ws.socket = throwingTask;
  ws._setState('connected');
  assert.strictEqual(ws.send({ type: 'fails' }), false);
  assert.strictEqual(ws.sendAudioFrame(new ArrayBuffer(1)), false);
  assert.strictEqual(errors.filter((entry) => entry.scope === 'send').length, 2);
  assert.deepStrictEqual(listenerStates, ['disconnected', 'connected']);
}

async function testConnectValidationAndCurrentClose() {
  const errors = [];
  const ws = new WebSocketManager({ onError: (error, scope) => errors.push({ message: error.message, scope }) });
  ws.connect('', 'device');
  assert.deepStrictEqual(errors, [{ message: 'wsUrl/deviceId required', scope: 'connect' }]);

  const task = makeSocketTask();
  let options;
  global.wx = { connectSocket(nextOptions) { options = nextOptions; return task; } };
  ws.connect('ws://example.test/v1?first=yes', 'device id', 'token value');
  assert.match(options.url, /first=yes&device-id=device%20id/);
  assert.match(options.url, /authorization=Bearer%20token%20value/);
  task.triggerError({ errMsg: 'current socket error' });
  assert.strictEqual(errors[1].scope, 'connect');
  task.triggerClose();
  assert.strictEqual(ws.socket, null);
  assert.strictEqual(ws.state, 'disconnected');
  ws.disconnect();
  ws.destroy();
}

async function runTests() {
  await testSerialVoiceSends();
  await testFailureIsStickyAndAbortIsDirect();
  await testTeardownRejectsQueuedWorkWithoutReplay();
  await testInvalidationRejectsInflightAndQueuedWorkPromptly();
  await testCurrentCloseRejectsInflightAndQueuedWorkPromptly();
  await testAbortDoesNotFollowAnInflightEnd();
  await testAbortCancelsAnEndQueuedBehindAudio();
  await testListenFeedbackAndLegacyImmediateSends();
  await testConnectionGenerationsIgnoreStaleHandlers();
  await testProtocolDispatchAndFailurePaths();
  await testConnectValidationAndCurrentClose();
  console.log('websocket.test.js: ALL PASS');
}

runTests().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
