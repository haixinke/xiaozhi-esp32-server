const { createBleSession } = require('../../utils/ble-debug/session');
const { makeRequest } = require('../../utils/ble-debug/protocol');
const { isBleDebugMode } = require('../../utils/ble-debug-entry');

const PHASE_LABELS = {
  idle: '未连接',
  scanning: '扫描中',
  connecting: '连接中',
  handshake: '校验协议',
  ready: '已就绪',
  offline: '已断开'
};
const PET_TYPES = { '1': '锦鲤', '2': '玉兔' };
const LIGHT_MODES = [
  { key: 'SOLID', label: 'SOLID · 常亮' },
  { key: 'BLINK', label: 'BLINK · 闪烁' },
  { key: 'BREATHE', label: 'BREATHE · 呼吸' },
  { key: 'ALTERNATE', label: 'ALTERNATE · 交替' },
  { key: 'CHASE', label: 'CHASE · 追逐' },
  { key: 'RAINBOW', label: 'RAINBOW · 彩虹' },
  { key: 'FLOW', label: 'FLOW · 流光' },
  { key: 'MARQUEE', label: 'MARQUEE · 跑马灯' }
];
const CHANNEL_FIELDS = [
  { key: 'red', label: '红 R' },
  { key: 'green', label: '绿 G' },
  { key: 'blue', label: '蓝 B' }
];
const LOG_LABELS = {
  tx: '发送',
  rx: '接收',
  'tx-part': '写入分包',
  'rx-part': '通知片段',
  event: '状态',
  error: '错误'
};
const LOG_LIMIT = 240;

function inRange(value, minimum, maximum) {
  return value !== '' && !/\D/.test(value) && Number(value) >= minimum && Number(value) <= maximum;
}

function packetLog(kind) {
  return kind === 'tx-part' || kind === 'rx-part';
}

function displayBytes(value) {
  return value.replace(/[\\\x00-\x1f\x7f-\xff]/g, (character) => {
    if (character === '\\') return '\\\\';
    if (character === '\n') return '\\n';
    if (character === '\r') return '\\r';
    return '\\x' + character.charCodeAt(0).toString(16).padStart(2, '0');
  });
}

function logTime(date) {
  const time = [date.getHours(), date.getMinutes(), date.getSeconds()]
    .map((part) => String(part).padStart(2, '0')).join(':');
  return time + '.' + String(date.getMilliseconds()).padStart(3, '0');
}

Page({
  data: {
    allowed: false,
    phase: 'idle',
    phaseLabel: PHASE_LABELS.idle,
    message: '开始扫描以查找附近的控制板',
    operationError: '',
    devices: [],
    deviceId: '',
    info: null,
    petType: '',
    status: null,
    canSend: false,
    canHatch: false,
    canStop: false,
    stopping: false,
    lightModes: LIGHT_MODES,
    lightModeIndex: 2,
    lightMode: 'BREATHE',
    channelFields: CHANNEL_FIELDS,
    lightValues: { red: '255', green: '120', blue: '30', brightness: '40', time: '1600' },
    lightError: '',
    rawLine: '',
    rawError: '',
    showPackets: false,
    logs: []
  },

  onLoad(options) {
    if (!isBleDebugMode(options)) {
      wx.reLaunch({ url: '/pages/home/home' });
      return;
    }
    this._logs = [];
    this._packetLogs = [];
    this._logId = 0;
    this.setData({ allowed: true });
    this._session = createBleSession(wx, {
      onChange: (snapshot) => this._updateStatus(snapshot),
      onLog: (entry) => this._record(entry)
    });
  },

  onShow() {
    if (this._session) return this._operate('setVisible', true);
  },

  onHide() {
    if (this._session) return this._operate('setVisible', false);
  },

  onUnload() {
    const session = this._session;
    this._session = null;
    this._logs = null;
    this._packetLogs = null;
    if (session) return session.dispose();
  },

  onBack() {
    if (getCurrentPages().length > 1) {
      wx.navigateBack();
      return;
    }
    wx.reLaunch({ url: '/pages/welcome/welcome?bleDebug=1' });
  },

  onToggleScan() {
    const { phase } = this.data;
    if (phase === 'scanning') return this._operate('stopScan');
    if (phase === 'idle' || phase === 'offline') return this._operate('scan');
  },

  onSelectDevice(event) {
    const { phase, devices, deviceId } = this.data;
    if (deviceId || phase === 'connecting' || phase === 'handshake') return;
    const selected = event.currentTarget.dataset.deviceId;
    if (devices.some((device) => device.deviceId === selected)) {
      return this._operate('connect', selected);
    }
  },

  onDisconnect() {
    return this._operate('disconnect');
  },

  onReadInfo() {
    if (this.data.canSend) return this._operate('send', '!INFO');
  },

  onReadStatus() {
    if (this.data.canSend) return this._operate('send', '!STATUS');
  },

  onHatch() {
    if (this.data.canHatch) return this._operate('send', '!HATCH');
  },

  onStop() {
    if (this.data.canStop) return this._operate('send', '!STOP');
  },

  onLightMode(event) {
    const index = Number(event.detail.value);
    if (!LIGHT_MODES[index]) return;
    this.setData({ lightModeIndex: index, lightMode: LIGHT_MODES[index].key, lightError: '' });
  },

  onLightField(event) {
    const { key } = event.currentTarget.dataset;
    const lightValues = this.data.lightValues;
    if (!Object.prototype.hasOwnProperty.call(lightValues, key)) return;
    this.setData({ lightValues: { ...lightValues, [key]: event.detail.value }, lightError: '' });
  },

  onApplyLight() {
    if (!this.data.canSend) return;
    const { lightMode, lightValues } = this.data;
    const rgb = lightMode === 'RAINBOW' ? ['0', '0', '0'] : CHANNEL_FIELDS.map(({ key }) => lightValues[key]);
    const time = lightMode === 'SOLID' ? '0' : lightValues.time;
    const colorValues = [...rgb, lightValues.brightness];
    if (!colorValues.every((value) => inRange(value, 0, 255))) {
      this.setData({ lightError: 'RGB 和亮度均须为 0 到 255 的整数' });
      return;
    }
    if (lightMode !== 'SOLID' && !inRange(time, 20, 60000)) {
      this.setData({ lightError: '时长须为 20 到 60000 毫秒的整数' });
      return;
    }
    this.setData({ lightError: '' });
    return this._operate('send', ['!LIGHT', lightMode, ...colorValues.map(Number), Number(time)].join(' '));
  },

  onLightOff() {
    if (this.data.canSend) return this._operate('send', '!LIGHT OFF');
  },

  onRawInput(event) {
    this.setData({ rawLine: event.detail.value, rawError: '' });
  },

  onSendRaw() {
    const { canSend, canStop, rawLine } = this.data;
    if (!canSend && !(canStop && rawLine === '!STOP')) return;
    try {
      makeRequest(rawLine);
    } catch (error) {
      this.setData({ rawError: error.message });
      return;
    }
    this.setData({ rawError: '' });
    return this._operate('send', rawLine);
  },

  onTogglePackets() {
    const showPackets = !this.data.showPackets;
    this.setData({ showPackets, logs: this._visibleLogs(showPackets) });
  },

  onClearLogs() {
    this._logs = [];
    this._packetLogs = [];
    this.setData({ logs: [] });
  },

  onCopyLogs() {
    const logs = this._visibleLogs(true);
    if (!logs.length) return;
    wx.setClipboardData({
      data: logs.reverse().map(({ time, label, display }) => time + '  ' + label + '  ' + display).join('\n'),
      fail: () => this.setData({ operationError: '复制失败，请重试' })
    });
  },

  async _operate(method, ...args) {
    if (!this._session) return;
    const session = this._session;
    if (this.data.operationError) this.setData({ operationError: '' });
    try {
      await session[method](...args);
    } catch (error) {
      if (this._session === session) {
        this.setData({ operationError: (error && (error.message || error.errMsg)) || '操作失败，请查看通信记录' });
      }
    }
  },

  _updateStatus({ phase, devices, deviceId, info, status, busy, stopping, message, canHatch }) {
    if (!this._logs) return;
    const ready = phase === 'ready';
    const canSend = ready && !busy;
    this.setData({
      phase,
      phaseLabel: PHASE_LABELS[phase],
      message,
      devices,
      deviceId,
      info,
      petType: info ? PET_TYPES[info.TYPE] : '',
      status,
      canSend,
      canHatch,
      canStop: ready && !stopping,
      stopping
    });
  },

  _record({ kind, text }) {
    if (!this._logs) return;
    const entry = {
      id: ++this._logId,
      kind,
      time: logTime(new Date()),
      label: LOG_LABELS[kind],
      display: kind === 'event' || kind === 'error' ? text : displayBytes(text)
    };
    const isPacket = packetLog(kind);
    // 分包记录可能远多于完整报文，分别保留两类记录以便追查。
    if (isPacket) this._packetLogs = [entry, ...this._packetLogs].slice(0, LOG_LIMIT);
    else this._logs = [entry, ...this._logs].slice(0, LOG_LIMIT);
    if (this.data.showPackets || !isPacket) {
      this.setData({ logs: this._visibleLogs(this.data.showPackets) });
    }
  },

  _visibleLogs(showPackets) {
    return showPackets ? [...this._logs, ...this._packetLogs].sort((a, b) => b.id - a.id) : this._logs;
  }
});
