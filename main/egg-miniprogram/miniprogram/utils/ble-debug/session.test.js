const assert = require('node:assert/strict');
const { setTimeout: realSetTimeout, clearTimeout: realClearTimeout } = require('node:timers');
const { createFakeBle, createManualClock } = require('../../../scripts/ble-fake');
const { createBleSession } = require('./session');

const BOARD = { deviceId: 'bench-board', name: 'Egg Test', RSSI: -45 };

async function withSession(scenario, options) {
  const clock = createManualClock();
  const ble = createFakeBle(options);
  const changes = [];
  const logs = [];
  const session = createBleSession(ble.wx, {
    onChange: state => changes.push(state),
    onLog: entry => logs.push(entry)
  });
  const snapshot = () => changes[changes.length - 1];
  try {
    await scenario({ ble, clock, session, snapshot, logs });
  } finally {
    try { await session.dispose(); }
    finally { clock.restore(); }
  }
}

async function connectBoard({ ble, clock, session }) {
  await session.scan();
  ble.advertise([BOARD]);
  const connecting = session.connect(BOARD.deviceId);
  await clock.advance(200);
  await connecting;
}

async function handshake({ ble, clock, session, snapshot }) {
  await session.scan();
  ble.advertise([BOARD, { deviceId: 'other', RSSI: -80 }]);
  ble.advertise([{ ...BOARD, RSSI: -30 }]);
  assert.equal(snapshot().phase, 'scanning');
  assert.deepEqual(snapshot().devices.map(device => device.deviceId).sort(), ['bench-board', 'other']);
  assert.equal(ble.calls.some(call => call.name === 'createBLEConnection'), false);
  const connecting = session.connect(BOARD.deviceId);
  await clock.advance(199);
  assert.equal(ble.writes.length, 0);
  assert.equal(snapshot().phase, 'handshake');
  await clock.advance(1);
  await connecting;
  assert.deepEqual(ble.writes.map(write => write.text), ['!INFO\n', '!STATUS\n']);
  const names = ble.calls.map(call => call.name);
  assert.ok(names.indexOf('stopBluetoothDevicesDiscovery') < names.indexOf('createBLEConnection'));
  assert.ok(names.indexOf('onBLECharacteristicValueChange') < names.indexOf('notifyBLECharacteristicValueChange'));
  assert.ok(names.indexOf('notifyBLECharacteristicValueChange') < names.indexOf('writeBLECharacteristicValue'));
  assert.equal(snapshot().phase, 'ready');
  assert.equal(snapshot().deviceId, BOARD.deviceId);
  assert.equal(snapshot().info.SN, '00112233445566778899AABB');
  assert.equal(snapshot().info.TYPE, '1');
  assert.equal(snapshot().status.HATCH, 'FRESH');
}

async function fragmentedRepliesAndAlternateProperties(context) {
  const { ble, clock, session, snapshot, logs } = context;
  ble.setResponder((frame, getDefaultReply) => {
    if (frame === '!INFO\n') {
      ble.notify('preamble!unfinished!OK INF');
      ble.notify('O PROTO=2 FW=2.0.1 LEDS=20 SN=000011223344');
      ble.notify('5566778899AA TYPE=2\r');
      ble.notify('\n');
      return null;
    }
    if (frame === '!STATUS\n') return `!OK LIGHT MODE=SOLID\n${getDefaultReply()}`;
    return getDefaultReply();
  });
  await connectBoard(context);
  assert.equal(snapshot().phase, 'ready');
  assert.equal(snapshot().info.TYPE, '2');
  assert.equal(snapshot().info.SN, '0000112233445566778899AA');
  assert.equal(snapshot().status.LIGHT, 'OFF');
  assert.equal(ble.calls.find(call => call.name === 'notifyBLECharacteristicValueChange').args.type, 'indication');
  assert.ok(ble.writes.every(write => write.args.writeType === 'writeNoResponse'));
  assert.ok(logs.some(log => log.kind === 'rx-part' && log.text.includes('unfinished')));
  assert.ok(logs.some(log => log.kind === 'rx' && log.text.endsWith('TYPE=2\r\n')));

  ble.setResponder(() => null);
  const waiting = session.send('!STATUS');
  await clock.settle();
  const status = '!OK STATUS HATCH=DONE MOTOR=0 FAULT=0 LIGHT=OFF\n';
  ble.notify(status, { deviceId: 'different-board' });
  ble.notify(status, { characteristicId: '0000FFF2-0000-1000-8000-00805F9B34FB' });
  ble.notify(status, { serviceId: '0000FFFF-0000-1000-8000-00805F9B34FB' });
  assert.equal(snapshot().status.HATCH, 'FRESH');
  assert.equal(snapshot().busy, true);
  ble.notify(status);
  await waiting;
  assert.equal(snapshot().status.HATCH, 'DONE');
}

async function legacyFirmware(context) {
  const { ble, clock, session, snapshot } = context;
  await session.scan();
  ble.advertise([BOARD]);
  const connecting = session.connect(BOARD.deviceId);
  const settled = connecting.catch(() => {});
  await clock.advance(200);
  await settled;
  assert.notEqual(snapshot().phase, 'ready');
  assert.ok(snapshot().message);
  assert.deepEqual(ble.writes.map(write => write.text), ['!INFO\n']);
  await assert.rejects(session.send('!LIGHT OFF'));
}

async function adapterUnavailable({ ble, session, snapshot }) {
  await session.scan().catch(() => {});
  assert.notEqual(snapshot().phase, 'scanning');
  assert.ok(snapshot().message);
  assert.equal(ble.calls.some(call => call.name === 'startBluetoothDevicesDiscovery'), false);
}

async function adapterTurnedOffDuringScan({ ble, clock, session, snapshot }) {
  await session.scan();
  ble.powerOff();
  await clock.settle();
  assert.equal(snapshot().phase, 'offline');
  assert.match(snapshot().message, /蓝牙不可用/);
  assert.ok(ble.calls.some(call => call.name === 'stopBluetoothDevicesDiscovery'));
}

async function rawCommandsAndPacketBoundaries(context) {
  const { ble, clock, session, snapshot, logs } = context;
  await connectBoard(context);
  ble.setResponder(frame => frame.startsWith('!LIGHT') ? '!ERR LIGHT PARAM' : '!ERR UNKNOWN CMD');
  const unknown = await session.send('!DIAGNOSTIC bad');
  assert.equal(unknown.ok, false);
  assert.equal(unknown.command, 'UNKNOWN');
  assert.equal(ble.writes.at(-1).text, '!DIAGNOSTIC bad\n');
  await assert.rejects(session.send('!你好'));
  await assert.rejects(session.send('!INFO\n!STOP'));
  await assert.rejects(session.send(`!${'x'.repeat(128)}`));
  assert.equal(ble.writes.length, 3);

  ble.setHoldWrites(true);
  const request = '!LIGHT BREATHE 255 120 30 40 1600';
  const waiting = session.send(request);
  await clock.settle();
  const first = ble.writes.at(-1);
  assert.equal(first.args.value.byteLength, 20);
  assert.equal(first.text, '!LIGHT BREATHE 255 1');
  await clock.advance(50);
  assert.equal(ble.writes.length, 4);
  ble.finishWrite();
  await clock.advance(29);
  assert.equal(ble.writes.length, 4);
  await clock.advance(1);
  const second = ble.writes.at(-1);
  assert.equal(second.text, '20 30 40 1600\n');
  assert.equal(snapshot().busy, true);
  ble.finishWrite();
  const reply = await waiting;
  assert.equal(reply.ok, false);
  assert.equal(reply.code, 'PARAM');
  assert.equal(snapshot().phase, 'ready');
  assert.ok(logs.some(log => log.kind === 'tx' && log.text === `${request}\n`));
  assert.ok(logs.some(log => log.kind === 'rx' && log.text === '!ERR LIGHT PARAM\n'));
}

async function stopPreemptsPartialWrite(context) {
  const { ble, clock, session, snapshot, logs } = context;
  await connectBoard(context);
  ble.setResponder(() => null);
  ble.setHoldWrites(true);
  const interrupted = session.send('!LIGHT BREATHE 255 120 30 40 1600');
  const interruption = interrupted.catch(error => error);
  await clock.settle();
  const stopping = session.send('!STOP');
  assert.equal(snapshot().stopping, true);
  await clock.settle();
  assert.equal(ble.writes.length, 3);
  ble.finishWrite();
  await clock.settle();
  assert.deepEqual(ble.writes.slice(2).map(write => write.text), ['!LIGHT BREATHE 255 1', '!STOP\n']);
  ble.finishWrite();
  ble.reply('!OK LIGHT MODE=BREATHE');
  await clock.settle();
  assert.equal(snapshot().stopping, true);
  ble.reply('!OK STOP MOTOR=0 HATCH=ABORTED');
  const reply = await stopping;
  assert.equal(reply.command, 'STOP');
  assert.equal(reply.ok, true);
  assert.ok(await interruption instanceof Error);
  assert.equal(snapshot().stopping, false);
  assert.equal(snapshot().status.HATCH, 'ABORTED');
  assert.ok(logs.some(log => log.kind === 'rx' && log.text === '!OK LIGHT MODE=BREATHE\n'));
}

async function stopCancelsPacketGap(context) {
  const { ble, clock, session, snapshot } = context;
  await connectBoard(context);
  ble.setResponder((frame, fallback) => frame === '!STOP\n' ? '!ERR STOP HARDWARE' : fallback());
  const first = assert.rejects(session.send('!LIGHT BREATHE 255 120 30 40 1600'));
  await clock.advance(10);
  const stop = await session.send('!STOP');
  assert.equal(stop.command, 'STOP');
  assert.equal(stop.ok, false);
  assert.equal(stop.code, 'HARDWARE');
  await first;
  await clock.advance(50);
  const afterHandshake = ble.writes.slice(2).map(write => write.text);
  assert.deepEqual(afterHandshake, ['!LIGHT BREATHE 255 1', '!STOP\n', '!STATUS\n']);
  assert.equal(snapshot().phase, 'ready');
}

async function stopDrainsLateStatusBeforeReuse(context) {
  const { ble, clock, session, snapshot, logs } = context;
  await connectBoard(context);
  let statusCalls = 0;
  ble.setResponder((frame, fallback) => {
    if (frame === '!STATUS\n' && ++statusCalls === 1) return null;
    return fallback();
  });

  const cancelled = assert.rejects(session.send('!STATUS'), /STOP/);
  await clock.settle();
  await session.send('!STOP');
  await cancelled;
  await session.send('!LIGHT OFF');
  await assert.rejects(session.send('!STATUS'), /STATUS.*应答/);
  assert.equal(statusCalls, 1);

  const staleStatus = '!OK STATUS HATCH=RUNNING MOTOR=1 FAULT=0 LIGHT=OFF';
  ble.reply(staleStatus);
  assert.equal(snapshot().status.HATCH, 'ABORTED');
  assert.ok(logs.some(({ kind, text }) => kind === 'rx' && text === `${staleStatus}\n`));
  await session.send('!STATUS');
  assert.equal(statusCalls, 2);
  assert.equal(snapshot().status.HATCH, 'FRESH');
}

async function coalescedCancelledStatusCannotCompleteForegroundRefresh(context) {
  const { ble, clock, session, snapshot, logs } = context;
  await connectBoard(context);
  ble.setResponder(() => null);
  const cancelled = assert.rejects(session.send('!STATUS'), /STOP/);
  await clock.settle();
  const stopped = session.send('!STOP');
  session.setVisible(false);
  session.setVisible(true);
  await clock.settle();

  const staleStatus = '!OK STATUS HATCH=RUNNING MOTOR=1 FAULT=0 LIGHT=OFF';
  ble.notify(`!OK STOP MOTOR=0 HATCH=ABORTED\n${staleStatus}\n`);
  await stopped;
  await cancelled;
  await clock.settle();
  assert.equal(snapshot().status.HATCH, 'ABORTED');
  assert.ok(logs.some(({ kind, text }) => kind === 'rx' && text === `${staleStatus}\n`));

  assert.equal(snapshot().busy, true, '回前台的 STATUS 在旧应答被丢弃后发出');
  assert.equal(ble.writes.filter(({ text }) => text === '!STATUS\n').length, 3);
  ble.reply('!OK STATUS HATCH=DONE MOTOR=0 FAULT=0 LIGHT=OFF');
  await clock.settle();
  assert.equal(snapshot().status.HATCH, 'DONE');
}

async function timeoutRequiresManualReconnect(context) {
  const { ble, clock, session, snapshot } = context;
  await connectBoard(context);
  ble.setResponder(frame => frame === '!HATCH\n' ? null : '!OK STATUS HATCH=DONE MOTOR=0 FAULT=0 LIGHT=OFF');
  const waiting = session.send('!HATCH');
  const settlement = assert.rejects(waiting);
  await clock.advance(1999);
  assert.equal(snapshot().phase, 'ready');
  assert.equal(snapshot().busy, true);
  await clock.advance(1);
  await settlement;
  assert.equal(snapshot().phase, 'offline');
  assert.match(snapshot().message, /超时|未知/);
  assert.ok(ble.calls.some(call => call.name === 'closeBLEConnection'));
  await clock.advance(5000);
  assert.deepEqual(ble.writes.map(write => write.text), ['!INFO\n', '!STATUS\n', '!HATCH\n']);

  ble.setResponder((frame, fallback) => frame === '!STATUS\n'
    ? '!OK STATUS HATCH=DONE MOTOR=0 FAULT=0 LIGHT=OFF'
    : fallback());
  await connectBoard(context);
  assert.equal(snapshot().phase, 'ready');
  assert.equal(snapshot().status.HATCH, 'DONE');
  assert.deepEqual(ble.writes.map(write => write.text), ['!INFO\n', '!STATUS\n', '!HATCH\n', '!INFO\n', '!STATUS\n']);
}

async function malformedReplyLeavesResultUnknown(context) {
  const { ble, session, snapshot, logs } = context;
  await connectBoard(context);
  ble.setResponder(() => '!OK LIGHT WRONG_KEY=OFF');
  await assert.rejects(session.send('!LIGHT OFF'));
  assert.equal(snapshot().phase, 'offline');
  assert.match(snapshot().message, /格式|字段|未知/);
  assert.ok(logs.some(log => log.kind === 'rx' && log.text === '!OK LIGHT WRONG_KEY=OFF\n'));
  assert.ok(ble.calls.some(call => call.name === 'closeBLEConnection'));
}

async function invalidFrameRetainsEscapedBytesInDefaultTrace(context) {
  const { ble, clock, session, snapshot, logs } = context;
  await connectBoard(context);
  ble.setResponder(() => null);
  const pending = assert.rejects(session.send('!LIGHT OFF'));
  await clock.settle();
  ble.notify('!OK LIGHT MODE=\x00OFF\n');
  await pending;
  assert.equal(snapshot().phase, 'offline');
  assert.ok(logs.some(({ kind, text }) => kind === 'error' && text.includes('!OK LIGHT MODE=\\x00')));
}

async function failedNativeWriteLeavesResultUnknown(context) {
  const { ble, clock, session, snapshot } = context;
  await connectBoard(context);
  ble.setHoldWrites(true);
  const pending = assert.rejects(session.send('!LIGHT OFF'));
  await clock.settle();
  ble.finishWrite({ errCode: 10007, errMsg: 'property not supported' });
  await pending;
  assert.equal(snapshot().phase, 'offline');
  assert.match(snapshot().message, /写入失败.*结果未知/);
  assert.ok(ble.calls.some(call => call.name === 'closeBLEConnection'));
}

async function unknownHatchStateBlocksStart(context) {
  const { ble, session, snapshot, logs } = context;
  await connectBoard(context);
  assert.equal(snapshot().status.HATCH, 'UNRECOGNIZED');
  assert.ok(logs.some(log => log.kind === 'rx' && log.text.includes('HATCH=UNRECOGNIZED')));
  await assert.rejects(session.send('!HATCH'));
  assert.equal(ble.writes.some(write => write.text === '!HATCH\n'), false);
}

async function disconnectStopsPendingAction(context) {
  const { ble, clock, session, snapshot } = context;
  await connectBoard(context);
  ble.setResponder(() => null);
  const pending = assert.rejects(session.send('!HATCH'));
  await clock.settle();
  assert.equal(snapshot().busy, true);
  ble.disconnect();
  await pending;
  assert.equal(snapshot().phase, 'offline');
  await session.setVisible(false);
  await session.setVisible(true);
  await clock.advance(5000);
  assert.deepEqual(ble.writes.map(write => write.text), ['!INFO\n', '!STATUS\n', '!HATCH\n']);
  assert.equal(ble.calls.filter(call => call.name === 'createBLEConnection').length, 1);
}

async function pauseAndResumeHatchPolling(context) {
  const { ble, clock, session, snapshot } = context;
  await connectBoard(context);
  let pollCount = 0;
  ble.setResponder((frame, fallback) => {
    if (frame === '!HATCH\n') return '!OK HATCH RUNNING';
    if (frame === '!STATUS\n') {
      pollCount += 1;
      return pollCount === 1
        ? '!OK STATUS HATCH=RUNNING MOTOR=1 FAULT=0 LIGHT=OFF'
        : '!OK STATUS HATCH=DONE MOTOR=0 FAULT=0 LIGHT=OFF';
    }
    return fallback();
  });
  await session.send('!HATCH');
  await session.setVisible(false);
  await clock.advance(1500);
  assert.equal(pollCount, 0);
  await session.setVisible(true);
  await clock.settle();
  assert.equal(pollCount, 1);
  assert.equal(snapshot().status.HATCH, 'RUNNING');
  await clock.advance(500);
  assert.equal(pollCount, 2);
  assert.equal(snapshot().status.HATCH, 'DONE');
  await clock.advance(1000);
  assert.equal(pollCount, 2);
  await session.dispose();
  assert.equal(ble.listenerCount('BLECharacteristicValueChange'), 0);
  assert.equal(ble.listenerCount('BLEConnectionStateChange'), 0);
  assert.equal(ble.listenerCount('BluetoothDeviceFound'), 0);
  assert.ok(ble.calls.some(call => call.name === 'closeBLEConnection'));
}

async function rejectedHatchRefreshesStatus(context) {
  const { ble, clock, session, snapshot } = context;
  await connectBoard(context);
  ble.setResponder((frame, fallback) => {
    if (frame === '!HATCH\n') return '!ERR HATCH LOCKED';
    if (frame === '!STATUS\n') return '!OK STATUS HATCH=DONE MOTOR=0 FAULT=0 LIGHT=OFF';
    return fallback();
  });
  const rejected = await session.send('!HATCH');
  assert.equal(rejected.ok, false);
  assert.equal(rejected.code, 'LOCKED');
  await clock.advance(500);
  assert.equal(snapshot().status.HATCH, 'DONE');
  assert.deepEqual(ble.writes.slice(2).map(write => write.text), ['!HATCH\n', '!STATUS\n']);
}

async function quickReentryWaitsForPreviousAdapterCleanup(context) {
  const { ble, clock, session } = context;
  await connectBoard(context);
  const nativeClose = ble.wx.closeBluetoothAdapter;
  let finishClose;
  ble.wx.closeBluetoothAdapter = args => {
    finishClose = () => nativeClose(args);
  };

  const cleanup = session.dispose();
  await clock.settle();
  const newSession = createBleSession(ble.wx);
  const nextScan = newSession.scan();
  try {
    assert.equal(typeof finishClose, 'function');
    await clock.settle();
    assert.equal(ble.calls.filter(call => call.name === 'openBluetoothAdapter').length, 1);
    ble.wx.closeBluetoothAdapter = nativeClose;
    finishClose();
    finishClose = null;
    await cleanup;
    await nextScan;
    const calls = ble.calls.map(call => call.name);
    assert.equal(calls.filter(name => name === 'openBluetoothAdapter').length, 2);
    assert.ok(calls.indexOf('closeBluetoothAdapter') < calls.lastIndexOf('openBluetoothAdapter'));
    assert.equal(ble.listenerCount('BluetoothDeviceFound'), 1);
  } finally {
    ble.wx.closeBluetoothAdapter = nativeClose;
    if (finishClose) finishClose();
    await newSession.dispose();
  }
}

async function run() {
  const cases = [
    [handshake],
    [fragmentedRepliesAndAlternateProperties, {
      writeProperties: { write: true, writeDefault: false, writeNoResponse: true },
      notifyProperties: { indicate: true }
    }],
    [legacyFirmware, { infoReply: '!OK INFO PROTO=1 FW=V5 LEDS=20 SN=00112233445566778899AABB TYPE=1' }],
    [adapterUnavailable, { fail: { openBluetoothAdapter: { errCode: 10001, errMsg: 'not available' } } }],
    [adapterTurnedOffDuringScan],
    [rawCommandsAndPacketBoundaries],
    [stopPreemptsPartialWrite],
    [stopCancelsPacketGap],
    [stopDrainsLateStatusBeforeReuse],
    [coalescedCancelledStatusCannotCompleteForegroundRefresh],
    [timeoutRequiresManualReconnect],
    [malformedReplyLeavesResultUnknown],
    [invalidFrameRetainsEscapedBytesInDefaultTrace],
    [failedNativeWriteLeavesResultUnknown],
    [unknownHatchStateBlocksStart, { statusReply: '!OK STATUS HATCH=UNRECOGNIZED MOTOR=0 FAULT=0 LIGHT=OFF' }],
    [disconnectStopsPendingAction],
    [pauseAndResumeHatchPolling],
    [rejectedHatchRefreshesStatus],
    [quickReentryWaitsForPreviousAdapterCleanup]
  ];
  for (const [scenario, options] of cases) {
    let deadline;
    try {
      await Promise.race([
        withSession(scenario, options),
        new Promise((_, reject) => {
          deadline = realSetTimeout(() => reject(new Error(`${scenario.name} did not complete`)), 2000);
        })
      ]);
    } finally {
      realClearTimeout(deadline);
    }
  }
  console.log('ble-debug/session.test.js: ALL PASS');
}

run().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
