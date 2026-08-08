const { post } = require('../../utils/request');
const petStore = require('../../utils/pet-store');
const auth = require('../../utils/auth');
const shareInvite = require('../../utils/share-invite');

Page({
  data: { code: '', error: '', canSubmit: false, success: null, submitting: false, sharedInvite: false },

  onLoad() {
    const session = auth.getSession();
    if (!session || auth.isExpired() || session.hasPhone !== true) {
      wx.reLaunch({ url: '/pages/home/home' });
      return;
    }
    const pending = shareInvite.getPending();
    if (pending && pending.code) {
      this.setData({ code: pending.code, canSubmit: true, sharedInvite: true });
    }
  },

  onCodeInput(e) {
    const code = e.detail.value;
    const pending = this.data.sharedInvite ? shareInvite.getPending() : null;
    const sharedInvite = !!(pending && code.trim().toUpperCase() === pending.code);
    if (this.data.sharedInvite && !sharedInvite) shareInvite.clearPending();
    this.setData({ code, error: '', canSubmit: code.trim().length > 0, sharedInvite });
  },

  async onValidate() {
    if (!this.data.canSubmit || this.data.submitting) return;
    const inviteCode = this.data.code.trim();
    this.setData({ submitting: true, error: '' });
    try {
      const petVO = await post('/pet/adopt', { inviteCode });
      petStore.savePetFromVO(petVO);
      if (this.data.sharedInvite) shareInvite.clearPending();
      this.setData({ success: { prototype: petVO.prototype, petType: petVO.prototype } });
      setTimeout(() => wx.switchTab({ url: '/pages/home/home' }), 1150);
    } catch (error) {
      this.setData({ error: error.userMessage || '操作失败，请稍后重试', submitting: false });
    }
  }
});
