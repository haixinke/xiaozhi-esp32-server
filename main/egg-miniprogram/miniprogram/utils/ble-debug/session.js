const { makeRequest } = require('./protocol');
const { createRequestLane } = require('./request-lane');

const SUBSCRIPTION_SETTLE_MS = 200;
const HATCH_POLL_MS = 500;
const DISCOVERY_INTERVAL_MS = 300;
const UUID_SUFFIX = '-0000-1000-8000-00805F9B34FB';
const STATUS_REQUEST = makeRequest('!STATUS');
const INFO_REQUEST = makeRequest('!INFO');
const pendingNativeCleanup = new WeakMap(); // 页面快速重进时，先等旧会话释放微信蓝牙适配器。

function matchesUuid(uuid, shortId) {
  const value = typeof uuid === 'string' ? uuid.toUpperCase() : '';
  return value === shortId || value === `0000${shortId}${UUID_SUFFIX}`;
}

function writeMode({ writeDefault, writeNoResponse, write } = {}) {
  if (writeDefault) return 'write';
  if (writeNoResponse) return 'writeNoResponse';
  if (write && writeDefault === undefined && writeNoResponse === undefined) return 'write';
  return '';
}

function platformError(action, cause) {
  const code = cause?.errCode ?? cause?.errno;
  const details = {
    112: '请在微信公众平台的《用户隐私保护指引》中补充「蓝牙」声明，生效后重新打开小程序再试',
    10001: '请打开手机蓝牙并检查微信蓝牙权限',
    10006: '蓝牙连接已断开',
    10009: '当前环境不支持 BLE',
    10012: '蓝牙操作超时'
  };
  const detail = details[code] || cause?.errMsg || cause?.message;
  const error = new Error(`${action}${detail ? `：${detail}` : ''}${code == null ? '' : `（${code}）`}`);
  error.code = code;
  return error;
}

function createBleSession(wxApi, { onChange = () => {}, onLog = () => {} } = {}) {
  let state = {
    phase: 'idle', devices: [], deviceId: '', info: null, status: null,
    busy: false, stopping: false, message: '开始扫描以选择控制板'
  };
  let link = null;
  let closeWork = Promise.resolve();
  let scanWork = Promise.resolve();
  let scanGeneration = 0;
  let discovering = false;
  let adapterDirty = false;
  let adapterReady = false;
  let adapterOpening = null;
  let listenersRegistered = false;
  let visible = true;
  let followUpStatus = false;
  let pollTimer = null;
  let disposed = false;
  let disposeWork = null;

  function log(kind, text) {
    if (!disposed) onLog({ kind, text });
  }

  function canStartHatch() {
    const { HATCH, MOTOR, FAULT } = state.status || {};
    return HATCH === 'FRESH' && MOTOR === '0' && FAULT === '0' && !followUpStatus;
  }

  function update(patch = {}) {
    state = { ...state, ...patch, busy: lane.busy, stopping: lane.stopping };
    if (!disposed) {
      const canHatch = state.phase === 'ready' && !lane.busy && canStartHatch() && !lane.waitingForCancelledReply('HATCH');
      onChange({ ...state, canHatch });
    }
  }

  function invoke(method, options, action) {
    return new Promise((resolve, reject) => {
      if (typeof wxApi[method] !== 'function') {
        reject(new Error(`${action}：当前环境不支持此蓝牙操作`));
        return;
      }
      try {
        wxApi[method]({ ...options, success: resolve, fail: (error) => reject(platformError(action, error)) });
      } catch (error) {
        reject(platformError(action, error));
      }
    });
  }

  function cancelPoll() {
    clearTimeout(pollTimer);
    pollTimer = null;
  }

  function scheduleStatus() {
    if (!visible || state.phase !== 'ready' || lane.busy || disposed) return;
    if (lane.waitingForCancelledReply('STATUS')) {
      if (followUpStatus) update({ message: '等待已取消的 STATUS 应答，也可断开后重新连接' });
      return;
    }
    if (followUpStatus) {
      followUpStatus = false;
      cancelPoll();
      lane.request(STATUS_REQUEST).catch(() => {});
    } else if (state.status?.HATCH === 'RUNNING' && pollTimer === null) {
      pollTimer = setTimeout(() => {
        pollTimer = null;
        if (!visible || state.phase !== 'ready' || lane.busy) return;
        lane.request(STATUS_REQUEST).catch(() => {});
      }, HATCH_POLL_MS);
    }
  }

  function statusAwaitingRefresh() {
    // 保留正在观察的动作记录供后续轮询，过期的电机和故障读数不再作为启动条件。
    return state.status?.HATCH === 'RUNNING' ? { HATCH: 'RUNNING' } : null;
  }

  function dropLink(reason, failure = false) {
    const previous = link;
    if (!previous) return closeWork;

    const message = lane.busy ? `${reason}，操作结果未知` : reason;
    link = null;
    cancelPoll();
    followUpStatus = false;
    previous.cancelSettle?.();
    state = { ...state, phase: 'offline', deviceId: '', info: null, status: null, message };
    log(failure || lane.busy ? 'error' : 'event', message);
    lane.reset(new Error(message));
    update();

    closeWork = closeWork.then(async () => {
      // 主动退出后，迟到的连接成功也需要关闭。
      if (!previous.opening) return;
      try {
        await previous.opening;
        await invoke('closeBLEConnection', { deviceId: previous.deviceId }, '关闭蓝牙连接失败');
      } catch (error) {
        if (previous.opened && error.code !== 10001 && error.code !== 10006) log('error', error.message);
      }
    });
    return closeWork;
  }

  function handleResponse(reply) {
    const { command, ok, fields, code } = reply;
    if (!ok) {
      const message = `板端拒绝 ${command}：${code}`;
      log('error', message);
      if (command === 'HATCH' || command === 'STOP') followUpStatus = true;
      if (['HATCH', 'STOP', 'STATUS'].includes(command)) update({ message, status: statusAwaitingRefresh() });
      else update({ message });
      return;
    }

    const message = ({
      HATCH: '板端已受理破壳，等待状态查询',
      STOP: '板端已确认停止',
      LIGHT: '板端已受理灯光设置'
    })[command] || `板端已应答 ${command}`;
    log('event', message);
    if (command === 'INFO') update({ info: fields, message });
    else {
      const status = { ...state.status, ...fields };
      // 受理应答未采样电机状态，等下一次 STATUS 给出实际读数。
      update({ status: command === 'HATCH' ? { ...status, MOTOR: null } : status, message });
    }
    if (state.status?.HATCH !== 'RUNNING') cancelPoll();
  }

  const lane = createRequestLane({
    writePacket: (value) => invoke('writeBLECharacteristicValue', { ...link.write, value }, '蓝牙写入失败'),
    onLog: log,
    onActivity() {
      update();
      scheduleStatus();
    },
    onCancelledReply() {
      update();
      scheduleStatus();
    },
    onResponse: handleResponse,
    onFailure: (error) => dropLink(error.message, true)
  });

  function receive({ deviceId, serviceId, characteristicId, value }) {
    if (!link?.write || link.deviceId !== deviceId || !matchesUuid(serviceId, 'FFF0') || !matchesUuid(characteristicId, 'FFF1')) return;
    lane.receive(value);
  }

  function found({ devices = [] }) {
    if (disposed || state.phase !== 'scanning') return;
    const list = new Map(state.devices.map((device) => [device.deviceId, device]));
    for (const { deviceId, name, localName, RSSI } of devices) {
      if (!deviceId) continue;
      const previous = list.get(deviceId);
      list.set(deviceId, {
        deviceId,
        name: name || previous?.name || '',
        localName: localName || previous?.localName || '',
        RSSI: RSSI ?? previous?.RSSI ?? null
      });
    }
    update({ devices: [...list.values()] });
  }

  function adapterChanged({ available }) {
    if (disposed || available !== false) return;
    adapterReady = false;
    const message = '蓝牙不可用，请打开手机蓝牙并检查微信蓝牙权限';
    stopScan().catch(() => {});
    if (link) dropLink(message, true);
    else {
      log('error', message);
      update({ phase: 'offline', message });
    }
  }

  function connectionChanged({ deviceId, connected }) {
    if (!disposed && link?.opened && link.deviceId === deviceId && connected === false) {
      dropLink('蓝牙连接已断开，请重新选择设备', true);
    }
  }

  const listeners = {
    BluetoothDeviceFound: found,
    BluetoothAdapterStateChange: adapterChanged,
    BLEConnectionStateChange: connectionChanged,
    BLECharacteristicValueChange: receive
  };

  function attachListeners() {
    if (listenersRegistered) return;
    for (const [name, listener] of Object.entries(listeners)) wxApi[`on${name}`]?.(listener);
    listenersRegistered = true;
  }

  function detachListeners() {
    if (!listenersRegistered) return;
    for (const [name, listener] of Object.entries(listeners)) {
      // 四种监听中，只有连接状态接口支持按回调移除。
      const args = name === 'BLEConnectionStateChange' ? [listener] : [];
      wxApi[`off${name}`]?.(...args);
    }
    listenersRegistered = false;
  }

  async function initializeAdapter() {
    try {
      await invoke('openBluetoothAdapter', { mode: 'central' }, '初始化蓝牙失败');
      adapterReady = true;
      adapterDirty = true;
    } catch (error) {
      // 系统蓝牙关闭时，微信仍可能完成适配器初始化。
      if (error.code === 10001) adapterDirty = true;
      throw error;
    }
    if (!disposed) attachListeners();
  }

  async function openAdapter() {
    if (disposed || adapterReady) return;
    const priorCleanup = pendingNativeCleanup.get(wxApi);
    if (priorCleanup) await priorCleanup;
    if (disposed || adapterReady) return;
    if (!adapterOpening) {
      adapterOpening = initializeAdapter().finally(() => { adapterOpening = null; });
    }
    await adapterOpening;
  }

  function queueScan(operation) {
    const work = scanWork.then(operation);
    scanWork = work.catch(() => {});
    return work;
  }

  async function scan() {
    if (disposed || link || state.phase === 'scanning') return;
    const generation = ++scanGeneration;
    update({ phase: 'scanning', devices: [], message: '正在扫描附近的蓝牙设备' });
    return queueScan(async () => {
      if (disposed || generation !== scanGeneration) return;
      try {
        await openAdapter();
        if (disposed || generation !== scanGeneration) return;
        await invoke('startBluetoothDevicesDiscovery', {
          allowDuplicatesKey: true, interval: DISCOVERY_INTERVAL_MS
        }, '扫描蓝牙设备失败');
        discovering = true;
      } catch (error) {
        if (disposed || generation !== scanGeneration) return;
        if (error.code === 10001) adapterReady = false;
        log('error', error.message);
        update({ phase: 'offline', message: error.message });
        throw error;
      }
    });
  }

  function stopScan() {
    const generation = ++scanGeneration;
    return queueScan(async () => {
      if (discovering) {
        try {
          await invoke('stopBluetoothDevicesDiscovery', {}, '停止扫描失败');
        } catch (error) {
          if (error.code !== 10000 && error.code !== 10001) {
            log('error', error.message);
            throw error;
          }
        }
        discovering = false;
      }
      if (generation === scanGeneration && state.phase === 'scanning') {
        update({ phase: 'idle', message: '已停止扫描，可选择列表中的设备' });
      }
    });
  }

  function assertCurrent(attempt) {
    if (disposed || link !== attempt) throw new Error('蓝牙连接已取消');
  }

  async function establish(attempt) {
    const { deviceId } = attempt;
    await stopScan();
    await closeWork;
    assertCurrent(attempt);
    await openAdapter();
    assertCurrent(attempt);
    attempt.opening = invoke('createBLEConnection', { deviceId, timeout: 10000 }, '连接控制板失败');
    await attempt.opening;
    attempt.opened = true;
    assertCurrent(attempt);
    update({ phase: 'handshake', message: '正在发现服务并验证 V2.0 协议' });

    const { services = [] } = await invoke('getBLEDeviceServices', { deviceId }, '读取 BLE 服务失败');
    assertCurrent(attempt);
    const serviceId = services.find(({ uuid }) => matchesUuid(uuid, 'FFF0'))?.uuid;
    if (!serviceId) throw new Error('控制板缺少 FFF0 服务');

    const { characteristics = [] } = await invoke('getBLEDeviceCharacteristics', { deviceId, serviceId }, '读取 BLE 特征失败');
    assertCurrent(attempt);
    const writer = characteristics.find(({ uuid }) => matchesUuid(uuid, 'FFF2'));
    const receiver = characteristics.find(({ uuid }) => matchesUuid(uuid, 'FFF1'));
    const writeType = writeMode(writer?.properties);
    if (!writeType) throw new Error('控制板缺少可写的 FFF2 特征');
    const properties = receiver?.properties || {};
    if (!properties.notify && !properties.indicate) throw new Error('控制板缺少可订阅的 FFF1 特征');

    attempt.write = { deviceId, serviceId, characteristicId: writer.uuid, writeType };
    const type = properties.notify ? 'notification' : 'indication';
    await invoke('notifyBLECharacteristicValueChange', {
      deviceId, serviceId, characteristicId: receiver.uuid, state: true, type
    }, '订阅板端应答失败');
    assertCurrent(attempt);
    await new Promise((resolve) => {
      const timer = setTimeout(resolve, SUBSCRIPTION_SETTLE_MS);
      attempt.cancelSettle = () => { clearTimeout(timer); resolve(); };
    });
    attempt.cancelSettle = null;
    assertCurrent(attempt);

    for (const request of [INFO_REQUEST, STATUS_REQUEST]) {
      const reply = await lane.request(request);
      assertCurrent(attempt);
      if (!reply.ok) throw new Error(`${reply.command} 握手失败：${reply.code}`);
    }
    update({ phase: 'ready', message: '控制板已就绪' });
    log('event', state.message);
    scheduleStatus();
  }

  async function connect(deviceId) {
    if (disposed || link) throw new Error('请先断开当前控制板连接');
    if (!deviceId) throw new Error('请选择要连接的控制板');
    const attempt = { deviceId, opened: false, opening: null, write: null, cancelSettle: null };
    link = attempt;
    update({ phase: 'connecting', deviceId, info: null, status: null, message: '正在连接控制板' });
    try {
      await establish(attempt);
    } catch (error) {
      if (link === attempt) {
        const closing = dropLink(error.message, true);
        if (discovering) update({ phase: 'scanning' });
        await closing;
      }
      throw error;
    }
  }

  async function send(line) {
    if (disposed || state.phase !== 'ready') throw new Error('请先连接并完成协议握手');
    const request = makeRequest(line);
    if (/^!HATCH *$/.test(line) && !canStartHatch()) {
      throw new Error('当前板端状态不允许启动破壳');
    }
    return lane.request(request);
  }

  function setVisible(next) {
    const wasVisible = visible;
    visible = next;
    if (!visible) cancelPoll();
    else if (!wasVisible && state.phase === 'ready') {
      followUpStatus = true;
      update({ status: statusAwaitingRefresh() });
      scheduleStatus();
    }
  }

  function dispose() {
    if (disposeWork) return disposeWork;
    disposed = true;
    detachListeners();
    const priorCleanup = pendingNativeCleanup.get(wxApi);
    disposeWork = Promise.all([
      priorCleanup,
      adapterOpening?.catch(() => {}),
      stopScan().catch(() => {}),
      dropLink('已退出蓝牙联调')
    ]).then(async () => {
      if (adapterDirty) await invoke('closeBluetoothAdapter', {}, '关闭蓝牙适配器失败').catch(() => {});
    }).finally(() => {
      if (pendingNativeCleanup.get(wxApi) === disposeWork) pendingNativeCleanup.delete(wxApi);
    });
    pendingNativeCleanup.set(wxApi, disposeWork);
    return disposeWork;
  }

  update();
  return { scan, stopScan, connect, disconnect: () => dropLink('已断开蓝牙连接'), send, setVisible, dispose };
}

module.exports = { createBleSession };
