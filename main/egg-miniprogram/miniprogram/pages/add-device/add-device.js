const { adoptPet } = require('../../utils/pet-api');
const petStore = require('../../utils/pet-store');
const auth = require('../../utils/auth');
const shareInvite = require('../../utils/share-invite');
const { parseInviteQr } = require('../../utils/invite-code');

Page({
  data: {
    error: '',
    success: null,
    scanning: false,
    submitting: false,
    sharedInvite: false
  },

  onLoad() {
    // 领取只要求有效微信会话，手机号绑定属于破壳后的账号门槛。
    if (!auth.hasValidSession()) {
      wx.reLaunch({ url: '/pages/home/home' });
      return;
    }
    this.setData({ sharedInvite: Boolean(shareInvite.getPending()) });
  },

  onUnload() {
    this.disposed = true;
    clearTimeout(this.returnHomeTimer);
  },

  async onScanCode() {
    if (this.data.scanning || this.data.submitting) return;
    this.setData({ scanning: true, error: '' });
    let result;
    try {
      result = await new Promise((resolve, reject) => {
        wx.scanCode({
          onlyFromCamera: false,
          scanType: ['qrCode'],
          success: resolve,
          fail: reject
        });
      });
    } catch (error) {
      if (this.disposed) return;
      const cancelled = error && /cancel/i.test(error.errMsg || '');
      this.setData({ scanning: false, error: cancelled ? '' : '扫码失败，请重试' });
      return;
    }
    if (this.disposed) return;
    this.setData({ scanning: false });
    const inviteCode = parseInviteQr(result && result.result);
    if (!inviteCode) {
      this.setData({ error: '请扫描蛋宝宝激活码二维码' });
      return;
    }
    return this.submitInvite(inviteCode);
  },

  onClaimSharedInvite() {
    if (this.data.scanning || this.data.submitting) return;
    const pending = shareInvite.getPending();
    if (!pending) {
      this.setData({ sharedInvite: false, error: '好友邀请已失效，请扫码领取' });
      return;
    }
    return this.submitInvite(pending.code);
  },

  async submitInvite(inviteCode) {
    const session = auth.getSession();
    if (!session || auth.isExpired()) {
      wx.reLaunch({ url: '/pages/home/home' });
      return;
    }
    this.setData({ submitting: true, error: '' });
    try {
      const petVO = await adoptPet(inviteCode);
      const currentSession = auth.getSession();
      if (!currentSession || String(currentSession.userId) !== String(session.userId)) {
        if (!this.disposed) wx.reLaunch({ url: '/pages/home/home' });
        return;
      }
      petStore.savePetFromVO(petVO);
      if (this.data.sharedInvite) shareInvite.clearPending();
      if (this.disposed) return;
      this.setData({ success: { petType: petVO.prototype } });
      this.returnHomeTimer = setTimeout(() => wx.switchTab({ url: '/pages/home/home' }), 1150);
    } catch (error) {
      if (!this.disposed) {
        this.setData({ error: (error && error.userMessage) || '操作失败，请稍后重试', submitting: false });
      }
    }
  }
});
