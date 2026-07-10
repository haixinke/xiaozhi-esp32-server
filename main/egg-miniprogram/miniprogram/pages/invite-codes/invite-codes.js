const petStore = require('../../utils/pet-store');
Page({
  data: { codes: [] },
  onShow() { const pet = petStore.getPet(); this.setData({ codes: pet ? pet.inviteCodes || [] : [] }); },
  onCopy(e) { wx.setClipboardData({ data: e.currentTarget.dataset.code }); }
});
