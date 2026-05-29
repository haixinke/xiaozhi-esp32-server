/**
 * components/voice-button/voice-button.js
 *
 * 语音按钮：长按录音、松开发送、滑出取消。
 * 通过 chatState 控制视觉状态：idle / listening / thinking / speaking。
 */
Component({
  options: {
    multipleSlots: false,
  },

  properties: {
    /** 当前会话状态：idle | listening | thinking | speaking */
    chatState: {
      type: String,
      value: 'idle',
    },
    /** 是否启用（连接断开时禁用） */
    disabled: {
      type: Boolean,
      value: false,
    },
  },

  data: {
    pressed: false,
    cancelled: false,
    _startY: 0,
  },

  methods: {
    _onTouchStart(e) {
      if (this.data.disabled) return;
      const touch = (e.touches && e.touches[0]) || {};
      this.setData({
        pressed: true,
        cancelled: false,
        _startY: touch.clientY || 0,
      });
      this.triggerEvent('start');
    },

    _onTouchMove(e) {
      if (!this.data.pressed) return;
      const touch = (e.touches && e.touches[0]) || {};
      const dy = (this.data._startY || 0) - (touch.clientY || 0);
      // 上滑超过 80px 视为取消
      const cancelled = dy > 80;
      if (cancelled !== this.data.cancelled) {
        this.setData({ cancelled });
        this.triggerEvent('cancelhint', { cancelled });
      }
    },

    _onTouchEnd() {
      if (!this.data.pressed) return;
      const cancelled = this.data.cancelled;
      this.setData({ pressed: false, cancelled: false });
      if (cancelled) {
        this.triggerEvent('cancel');
      } else {
        this.triggerEvent('end');
      }
    },

    _onTouchCancel() {
      if (!this.data.pressed) return;
      this.setData({ pressed: false, cancelled: false });
      this.triggerEvent('cancel');
    },

    _onTapAbort() {
      if (this.data.disabled) return;
      this.triggerEvent('abort');
    },
  },
});
