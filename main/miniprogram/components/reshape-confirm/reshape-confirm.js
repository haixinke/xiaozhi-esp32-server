/**
 * reshape-confirm：重塑命运二次确认底部面板。
 * props: show, title, from, to, voucherName, remainCount, listenable, audioUrl
 * event: confirm（点击「确认重塑」）, listen（点试听）, close（遮罩/关闭）
 */
Component({
  properties: {
    show: { type: Boolean, value: false },
    title: { type: String, value: '为她重塑' },
    from: { type: String, value: '' },
    to: { type: String, value: '' },
    voucherName: { type: String, value: '' },
    remainCount: { type: Number, value: 0 },
    listenable: { type: Boolean, value: false },
    audioUrl: { type: String, value: '' }
  },
  methods: {
    onOverlay() { if (this.data.show) this.triggerEvent('close'); },
    onStop() {},
    onConfirm() { this.triggerEvent('confirm'); },
    onListen() { this.triggerEvent('listen'); }
  }
});
