// components/floating-call-ball/floating-call-ball.js
const VoiceCallManager = require('../../utils/voice-call-manager');

function formatDuration(totalSeconds) {
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  const pad = (n) => (n < 10 ? '0' + n : '' + n);
  return pad(m) + ':' + pad(s);
}

Component({
  properties: {
    darkMode: { type: Boolean, value: false },
    companionAvatar: { type: String, value: '' },
    top: { type: Number, value: 400 },
  },

  data: {
    visible: false,
    expanded: false,
    formattedDuration: '00:00',
  },

  _mgr: null,
  _unsubscribe: null,

  lifetimes: {
    attached() {
      this._mgr = VoiceCallManager();
      this._unsubscribe = (state) => this._sync(state);
      this._mgr.onStateChange(this._unsubscribe);
      this._sync(this._mgr.getState());
    },
    detached() {
      if (this._mgr && this._unsubscribe) {
        this._mgr.offStateChange(this._unsubscribe);
      }
    },
  },

  methods: {
    _sync(state) {
      this.setData({
        visible: state.state === 'connected',
        formattedDuration: formatDuration(state.durationSeconds),
      });
      if (state.state === 'ended') {
        this.setData({ expanded: false });
      }
    },

    onTap() {
      this.setData({ expanded: !this.data.expanded });
    },

    onActionsCatch() {
      // 阻止冒泡，避免点击操作面板时收起小球
    },

    onBackToCall() {
      this.setData({ expanded: false });
      wx.navigateTo({ url: '/pages/voice-call/voice-call' });
    },

    onHangup() {
      this.setData({ expanded: false });
      this._mgr.hangup();
    },
  },
});
