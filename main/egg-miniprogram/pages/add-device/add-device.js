/* 添加蛋宝宝
   新流程：扫码 / 输入凭证码 → 校验 → 轻惊喜反馈（只报原型）→ 自动进孵化首页。
   无 WiFi 配网、无取名步骤（取名在孵化互动中完成）。
   凭证校验、扫码调 wx.scanCode 均需接真实接口，此处为模拟。 */
Page({
  data: {
    mode: 'code',           // 'scan' | 'code'
    scanState: 'idle',      // 'idle' | 'scanning' | 'scanned'
    code: '',
    error: '',
    canSubmit: false,
    success: null           // { prototype }
  },

  onModeScan() { this.setData({ mode: 'scan', error: '' }); this.refreshSubmit(); },
  onModeCode() { this.setData({ mode: 'code', error: '' }); this.refreshSubmit(); },

  onCodeInput(e) { this.setData({ code: e.detail.value, error: '' }); this.refreshSubmit(); },

  onScan() {
    if (this.data.scanState !== 'idle') return;
    this.setData({ scanState: 'scanning' });
    // 真实项目：wx.scanCode({ success: res => ... })
    setTimeout(() => {
      const code = 'QY' + Math.floor(1000 + Math.random() * 8999);
      this.setData({ scanState: 'scanned', code });
      this.refreshSubmit();
    }, 900);
  },

  refreshSubmit() {
    const ok = this.data.mode === 'scan'
      ? this.data.scanState === 'scanned'
      : this.data.code.trim().length > 0;
    this.setData({ canSubmit: ok });
  },

  onValidate() {
    if (!this.data.canSubmit) return;
    const code = this.data.code.trim();
    // 模拟无效码
    if (code.toLowerCase() === 'invalid') {
      this.setData({ error: '邀请码无效，请检查后重试' });
      return;
    }
    // 模拟：由后端返回原型（玉兔 / 锦鲤），款式破壳才揭晓
    const prototype = code.length % 2 === 0 ? '锦鲤' : '玉兔';
    this.setData({ success: { prototype } });

    // ~1s 轻惊喜后自动进孵化首页
    setTimeout(() => {
      const newId = 'new';   // 真实项目用后端返回的设备 id
      wx.redirectTo({ url: `/pages/egg-state/egg-state?id=${newId}` });
    }, 1100);
  }
});
