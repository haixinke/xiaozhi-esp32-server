const SERVICE = '0000FFF0-0000-1000-8000-00805F9B34FB';
const RESPONSE = '0000FFF1-0000-1000-8000-00805F9B34FB';
const REQUEST = '0000FFF2-0000-1000-8000-00805F9B34FB';
const INFO_REPLY = '!OK INFO PROTO=2 FW=DEV LEDS=20 SN=00112233445566778899AABB TYPE=1';
const STATUS_REPLY = '!OK STATUS HATCH=FRESH MOTOR=0 FAULT=0 LIGHT=OFF';

function createFakeBle(options = {}) {
  const calls = [];
  const writes = [];
  const pendingWrites = [];
  const listeners = new Map();
  let connectedDevice = '';
  let available = true;
  let discovering = false;
  let holdWrites = false;
  let input = '';

  function emit(event, payload) {
    for (const listener of listeners.get(event) || []) listener(payload);
  }

  function notify(text, overrides = {}) {
    const bytes = Uint8Array.from(Buffer.from(text, 'latin1'));
    emit('BLECharacteristicValueChange', {
      deviceId: connectedDevice,
      serviceId: SERVICE,
      characteristicId: RESPONSE,
      value: bytes.buffer,
      ...overrides
    });
  }

  function reply(line) {
    notify(line.endsWith('\n') ? line : `${line}\n`);
  }

  function defaultReply(frame) {
    if (frame === '!INFO\n') return options.infoReply || INFO_REPLY;
    if (frame === '!STATUS\n') return options.statusReply || STATUS_REPLY;
    if (frame === '!LIGHT OFF\n') return '!OK LIGHT MODE=OFF';
    if (frame === '!STOP\n') return '!OK STOP MOTOR=0 HATCH=ABORTED';
    return '!ERR UNKNOWN CMD';
  }
  let responder = defaultReply;

  function acceptWrite(pending) {
    if (pending.text.startsWith('!')) input = '';
    input += pending.text;
    pending.args.success({});
    const end = input.indexOf('\n');
    if (end < 0) return;
    const frame = input.slice(0, end + 1);
    input = input.slice(end + 1);
    const response = responder(frame, () => defaultReply(frame));
    if (response != null) reply(response);
  }

  function api(name, run = () => ({})) {
    return args => {
      calls.push({ name, args });
      const failure = options.fail && options.fail[name];
      if (failure) args.fail(failure);
      else args.success(run(args));
    };
  }

  const wx = {
    openBluetoothAdapter: api('openBluetoothAdapter'),
    closeBluetoothAdapter: api('closeBluetoothAdapter'),
    getBluetoothAdapterState: api('getBluetoothAdapterState', () => ({ available, discovering })),
    startBluetoothDevicesDiscovery: api('startBluetoothDevicesDiscovery', () => {
      discovering = true;
      return {};
    }),
    stopBluetoothDevicesDiscovery: api('stopBluetoothDevicesDiscovery', () => {
      discovering = false;
      return {};
    }),
    getBluetoothDevices: api('getBluetoothDevices', () => ({ devices: options.cachedDevices || [] })),
    createBLEConnection: api('createBLEConnection', args => {
      connectedDevice = args.deviceId;
      return {};
    }),
    closeBLEConnection: api('closeBLEConnection', () => {
      connectedDevice = '';
      input = '';
      return {};
    }),
    getConnectedBluetoothDevices: api('getConnectedBluetoothDevices', () => ({
      devices: connectedDevice ? [{ deviceId: connectedDevice }] : []
    })),
    getBLEDeviceServices: api('getBLEDeviceServices', () => ({
      services: options.services || [{ uuid: SERVICE, isPrimary: true }]
    })),
    getBLEDeviceCharacteristics: api('getBLEDeviceCharacteristics', () => ({
      characteristics: options.characteristics || [
        { uuid: REQUEST, properties: options.writeProperties || { write: true } },
        { uuid: RESPONSE, properties: options.notifyProperties || { notify: true } }
      ]
    })),
    notifyBLECharacteristicValueChange: api('notifyBLECharacteristicValueChange'),
    writeBLECharacteristicValue(args) {
      calls.push({ name: 'writeBLECharacteristicValue', args });
      const write = { args, text: Buffer.from(args.value).toString('latin1') };
      writes.push(write);
      if (holdWrites) pendingWrites.push(write);
      else acceptWrite(write);
    }
  };

  for (const event of ['BluetoothDeviceFound', 'BluetoothAdapterStateChange', 'BLEConnectionStateChange', 'BLECharacteristicValueChange']) {
    wx[`on${event}`] = callback => {
      calls.push({ name: `on${event}` });
      const eventListeners = listeners.get(event) || new Set();
      eventListeners.add(callback);
      listeners.set(event, eventListeners);
    };
    wx[`off${event}`] = callback => {
      calls.push({ name: `off${event}` });
      if (callback) listeners.get(event)?.delete(callback);
      else listeners.delete(event);
    };
  }

  return {
    wx,
    calls,
    writes,
    pendingWrites,
    emit,
    notify,
    reply,
    listenerCount: event => listeners.get(event)?.size || 0,
    advertise: devices => emit('BluetoothDeviceFound', { devices }),
    setResponder: handler => { responder = handler; },
    setHoldWrites: hold => { holdWrites = hold; },
    finishWrite(error) {
      const pending = pendingWrites.shift();
      if (error) pending.args.fail(error);
      else acceptWrite(pending);
    },
    disconnect() {
      const deviceId = connectedDevice;
      connectedDevice = '';
      input = '';
      emit('BLEConnectionStateChange', { deviceId, connected: false });
    },
    powerOff() {
      available = false;
      discovering = false;
      emit('BluetoothAdapterStateChange', { available, discovering });
    }
  };
}

function createManualClock() {
  const realSetTimeout = global.setTimeout;
  const realClearTimeout = global.clearTimeout;
  const scheduled = new Map();
  let now = 0;
  let nextId = 0;
  global.setTimeout = (callback, delay = 0, ...args) => {
    const id = ++nextId;
    scheduled.set(id, { at: now + delay, callback: () => callback(...args) });
    return id;
  };
  global.clearTimeout = id => { scheduled.delete(id); };
  const settle = () => new Promise(resolve => setImmediate(resolve));

  return {
    settle,
    async advance(duration) {
      const target = now + duration;
      await settle();
      while (true) {
        const due = [...scheduled].filter(([, timer]) => timer.at <= target)
          .sort(([, first], [, second]) => first.at - second.at)[0];
        if (!due) break;
        const [id, timer] = due;
        scheduled.delete(id);
        now = timer.at;
        timer.callback();
        await settle();
      }
      now = target;
    },
    restore() {
      scheduled.clear();
      global.setTimeout = realSetTimeout;
      global.clearTimeout = realClearTimeout;
    }
  };
}

module.exports = { createFakeBle, createManualClock };
