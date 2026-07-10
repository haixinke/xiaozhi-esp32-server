const petStore = require('../../utils/pet-store');

Page({
  data: { code: '', error: '', canSubmit: false, success: null, submitting: false },

  onCodeInput(e) {
    const code = e.detail.value;
    this.setData({ code, error: '', canSubmit: code.trim().length > 0 });
  },

  onValidate() {
    if (!this.data.canSubmit || this.data.submitting) return;
    this.setData({ submitting: true, error: '' });
    const result = petStore.bindPet(this.data.code);
    if (!result.ok) {
      this.setData({ error: result.message, submitting: false });
      return;
    }
    this.setData({ success: { prototype: result.pet.prototype } });
    setTimeout(() => wx.switchTab({ url: '/pages/home/home' }), 1150);
  }
});
