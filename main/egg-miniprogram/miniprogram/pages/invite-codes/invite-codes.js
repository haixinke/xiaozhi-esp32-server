const petStore = require('../../utils/pet-store');

Page({
  data: {
    inviteCode: null,
    exhausted: false
  },
  onShow() {
    this.loadInviteCode();
  },
  loadInviteCode() {
    // 当前从本地 mock 读取；接入后端后改为 GET /invite/mine（鉴权 normal），返回单个码 + quota/usedCount/remaining
    const pet = petStore.getPet();
    const code = pet && pet.inviteCode ? pet.inviteCode : null;
    this.setData({
      inviteCode: code,
      exhausted: code ? code.remaining <= 0 || code.status !== 1 : true
    });
  },
  onCopy() {
    if (!this.data.inviteCode || this.data.exhausted) return;
    wx.setClipboardData({ data: this.data.inviteCode.code });
  }
});
