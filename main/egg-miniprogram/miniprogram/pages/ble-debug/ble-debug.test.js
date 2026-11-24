const assert = require('node:assert/strict');
const { setTimeout: realSetTimeout, clearTimeout: realClearTimeout } = require('node:timers');
const { createFakeBle, createManualClock } = require('../../../scripts/ble-fake');

const originalPage = global.Page;
const originalWx = global.wx;
let pageDefinition;
let launchUrl;
let clipboard;
let fake;
let clock;

global.Page = (definition) => { pageDefinition = definition; };

function setup(envVersion = 'develop') {
  if (clock) clock.restore();
  fake = createFakeBle();
  clock = createManualClock();
  launchUrl = undefined;
  clipboard = undefined;
  global.wx = {
    ...fake.wx,
    getAccountInfoSync: () => ({ miniProgram: { envVersion } }),
    reLaunch({ url }) { launchUrl = url; },
    setClipboardData({ data }) { clipboard = data; }
  };
  const page = {
    ...pageDefinition,
    data: { ...pageDefinition.data },
    setData(delta) { this.data = { ...this.data, ...delta }; }
  };
  return page;
}

function input(value, key) {
  return { detail: { value }, currentTarget: { dataset: { key } } };
}

async function command(page, method, expected) {
  const offset = fake.writes.length;
  const sent = page[method]();
  await clock.advance(250);
  await sent;
  assert.equal(fake.writes.slice(offset).map(({ text }) => text).join(''), expected);
}

async function connect(page) {
  await page.onToggleScan();
  assert.equal(page.data.phase, 'scanning');
  fake.advertise([{ deviceId: 'board-a' }, { deviceId: 'board-b', localName: 'Pet egg' }]);
  assert.deepEqual(page.data.devices.map(({ deviceId }) => deviceId), ['board-a', 'board-b']);
  const connection = page.onSelectDevice({ currentTarget: { dataset: { deviceId: 'board-b' } } });
  await clock.advance(250);
  await connection;
  assert.equal(page.data.deviceId, 'board-b');
  assert.equal(page.data.phase, 'ready');
  assert.equal(page.data.info.SN, '00112233445566778899AABB');
  assert.equal(page.data.petType, '锦鲤');
  assert.equal(page.data.canSend, true);
  assert.equal(page.data.canStop, true);
}

async function testScanStop(page) {
  await page.onToggleScan();
  const stopDiscovery = wx.stopBluetoothDevicesDiscovery;
  wx.stopBluetoothDevicesDiscovery = ({ fail }) => fail({ errCode: 10008 });
  try {
    await page.onToggleScan();
    assert.equal(page.data.phase, 'scanning', '扫描停止失败时仍可重试停止');
    assert.match(page.data.operationError, /停止扫描失败/);
  } finally {
    wx.stopBluetoothDevicesDiscovery = stopDiscovery;
  }
  await page.onToggleScan();
  assert.equal(page.data.phase, 'idle');
}

async function testLighting(page) {
  await command(page, 'onApplyLight', '!LIGHT BREATHE 255 120 30 40 1600\n');
  const modes = ['SOLID', 'BLINK', 'BREATHE', 'ALTERNATE', 'CHASE', 'RAINBOW', 'FLOW', 'MARQUEE'];
  page.onLightField(input('0', 'brightness'));
  page.onLightField(input('20', 'time'));
  for (let index = 0; index < modes.length; index += 1) {
    const mode = modes[index];
    page.onLightMode(input(String(index)));
    const color = mode === 'RAINBOW' ? '0 0 0' : '255 120 30';
    const time = mode === 'SOLID' ? '0' : '20';
    await command(page, 'onApplyLight', '!LIGHT ' + mode + ' ' + color + ' 0 ' + time + '\n');
  }
  page.onLightField(input('60000', 'time'));
  await command(page, 'onApplyLight', '!LIGHT MARQUEE 255 120 30 0 60000\n');
  await command(page, 'onLightOff', '!LIGHT OFF\n');
  page.onLightField(input('19', 'time'));
  await command(page, 'onApplyLight', '');
  assert.match(page.data.lightError, /20 到 60000/);
  page.onLightField(input('60001', 'time'));
  await command(page, 'onApplyLight', '');
  assert.match(page.data.lightError, /20 到 60000/);
  page.onLightField(input('256', 'red'));
  await command(page, 'onApplyLight', '');
  assert.match(page.data.lightError, /0 到 255/);
}

async function testRawAndLogs(page) {
  page.onRawInput(input('!DIAG   007'));
  await command(page, 'onSendRaw', '!DIAG   007\n');
  assert.ok(page.data.logs.some(({ display }) => display === '!ERR UNKNOWN CMD\\n'));
  const longestLine = '!BOGUS ' + 'A'.repeat(121);
  page.onRawInput(input(longestLine));
  await command(page, 'onSendRaw', longestLine + '\n');
  for (const malformed of [longestLine + 'A', '!INFO\n', '!命令', 'INFO', '!INFO !HATCH']) {
    page.onRawInput(input(malformed));
    await command(page, 'onSendRaw', '');
    assert.match(page.data.rawError, /ASCII/);
  }

  assert.ok(page.data.logs.length);
  assert.ok(page.data.logs.every(({ kind }) => !kind.endsWith('-part')));
  page.onTogglePackets();
  assert.ok(page.data.logs.some(({ kind }) => kind === 'tx-part'));
  assert.ok(page.data.logs.some(({ kind }) => kind === 'rx-part'));

  page.onTogglePackets();
  for (let index = 0; index < 240; index += 1) fake.notify('x');
  page.onTogglePackets();
  page.onTogglePackets();
  assert.ok(page.data.logs.some(({ display }) => display === '!ERR UNKNOWN CMD\\n'), '隐藏片段不挤掉完整报文');

  page.onCopyLogs();
  assert.match(clipboard, /\d{2}:\d{2}:\d{2}\.\d{3} {2}发送 {2}!INFO\\n/);
  assert.match(clipboard, /!ERR UNKNOWN CMD\\n/);
  page.onClearLogs();
  assert.equal(page.data.logs.length, 0);
  page.onTogglePackets();
  assert.equal(page.data.logs.length, 0);
}

async function testHatchLifecycle(page) {
  let hatch = 'FRESH';
  fake.setResponder((frame, defaultReply) => {
    if (frame === '!HATCH\n') {
      hatch = 'RUNNING';
      return '!OK HATCH RUNNING';
    }
    if (frame === '!STATUS\n') return '!OK STATUS HATCH=' + hatch + ' MOTOR=0 FAULT=0 LIGHT=OFF';
    return defaultReply();
  });

  assert.equal(page.data.canHatch, true);
  await command(page, 'onHatch', '!HATCH\n');
  assert.equal(page.data.canHatch, false);
  assert.equal(page.data.status.MOTOR, null, '破壳受理后等待新的电机状态采样');
  assert.equal(page.data.canStop, true);
  const writesBeforeHide = fake.writes.length;
  await page.onHide();
  await clock.advance(1200);
  assert.equal(fake.writes.length, writesBeforeHide, '切后台期间暂停状态轮询');
  hatch = 'DONE';
  await command(page, 'onShow', '!STATUS\n');
  assert.equal(page.data.status.HATCH, 'DONE');
  await command(page, 'onHatch', '');

  await command(page, 'onStop', '!STOP\n');
  assert.equal(page.data.status.MOTOR, '0');
  assert.equal(page.data.status.HATCH, 'ABORTED');
}

async function testRejectedHatch(page) {
  fake.setResponder((frame, defaultReply) => {
    if (frame === '!HATCH\n') return '!ERR HATCH LOCKED';
    if (frame === '!STATUS\n') return '!ERR STATUS HARDWARE';
    return defaultReply();
  });
  assert.equal(page.data.canHatch, true);
  await command(page, 'onHatch', '!HATCH\n!STATUS\n');
  assert.equal(page.data.canHatch, false, '状态查询被拒绝后，旧 FRESH 状态不再有效');
  await command(page, 'onHatch', '');
}

async function run() {
  require('./ble-debug');
  assert.ok(pageDefinition, '蓝牙联调页已注册');

  const released = setup('release');
  released.onLoad({ bleDebug: '1' });
  assert.equal(launchUrl, '/pages/home/home');
  assert.equal(fake.calls.length, 0, '正式版直接进入时不初始化蓝牙');

  const page = setup();
  page.onLoad({ bleDebug: '1' });
  await page.onShow();
  assert.equal(page.data.allowed, true);
  assert.equal(page.data.canSend, false);
  assert.ok(fake.calls.every(({ name }) => name !== 'openBluetoothAdapter'), '仅当测试人员主动扫描时打开蓝牙');

  await testScanStop(page);
  await connect(page);
  fake.setResponder((frame, defaultReply) => {
    const light = /^!LIGHT ([A-Z]+)/.exec(frame);
    return light ? '!OK LIGHT MODE=' + light[1] : defaultReply();
  });
  await testLighting(page);
  await testRawAndLogs(page);
  await testHatchLifecycle(page);
  await page.onUnload();
  assert.ok(fake.calls.some(({ name }) => name === 'closeBLEConnection'));
  assert.equal(fake.listenerCount('BLECharacteristicValueChange'), 0);

  const rejected = setup();
  rejected.onLoad({ bleDebug: '1' });
  await connect(rejected);
  await testRejectedHatch(rejected);
  await rejected.onUnload();
  console.log('ble-debug.test.js: ALL PASS');
}

const watchdog = realSetTimeout(() => { throw new Error('Page integration test did not complete'); }, 5000);
run().finally(() => {
  realClearTimeout(watchdog);
  if (clock) clock.restore();
  global.Page = originalPage;
  global.wx = originalWx;
});
