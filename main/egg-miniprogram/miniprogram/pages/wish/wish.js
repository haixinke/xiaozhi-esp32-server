const petStore = require('../../utils/pet-store');
Page({
  data: { selected: '', options: ['安静陪伴你', '活泼逗你开心', '聪明帮你出主意'] },
  onSelect(e) { this.setData({ selected: e.currentTarget.dataset.value }); },
  async onSubmit() {
    if (!this.data.selected) return wx.showToast({ title: '先选一个愿望吧', icon: 'none' });
    const result = await petStore.completeWish(this.data.selected);
    if (!result.ok) return wx.showToast({ title: result.message, icon: 'none' });
    wx.showToast({ title: result.alreadyDone ? '今天已经许过愿啦' : '它记住了', icon: 'none' });
    setTimeout(() => wx.navigateBack(), 700);
  }
});
