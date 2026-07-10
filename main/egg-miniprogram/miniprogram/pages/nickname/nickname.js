const petStore = require('../../utils/pet-store');

Page({
  data: { name: '', count: 0, error: '' },
  onLoad() {
    const pet = petStore.getPet();
    const name = pet ? pet.name : '';
    this.setData({ name, count: Array.from(name).length });
  },
  onInput(e) {
    const name = e.detail.value;
    this.setData({ name, count: Array.from(name).length, error: '' });
  },
  onSave() {
    const result = petStore.updateNickname(this.data.name);
    if (!result.ok) return this.setData({ error: result.message });
    wx.showToast({ title: result.added ? '它记住了 · 进度 +20%' : '昵称已更新', icon: 'none' });
    setTimeout(() => wx.navigateBack(), 700);
  }
});
