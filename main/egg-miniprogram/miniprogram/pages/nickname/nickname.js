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
  async onSave() {
    const result = await petStore.updateNickname(this.data.name);
    if (!result.ok) return this.setData({ error: result.message });
    wx.showToast({ title: result.alreadyDone ? '昵称已更新' : '它记住了自己的名字', icon: 'none' });
    setTimeout(() => wx.navigateBack(), 700);
  }
});
