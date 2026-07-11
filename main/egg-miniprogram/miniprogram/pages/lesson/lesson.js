const petStore = require('../../utils/pet-store');
Page({
  data: { selected: '', options: [{ icon: '♡', value: '学会撒娇' }, { icon: '✦', value: '学会勇敢' }, { icon: '☺', value: '学会讲冷笑话' }] },
  onSelect(e) { this.setData({ selected: e.currentTarget.dataset.value }); },
  async onSubmit() {
    if (!this.data.selected) return wx.showToast({ title: '先选一堂课吧', icon: 'none' });
    const result = await petStore.completeLesson(this.data.selected);
    if (!result.ok) return wx.showToast({ title: result.message, icon: 'none' });
    wx.showToast({ title: result.alreadyDone ? '今天已经上过课啦' : '它认真听完了', icon: 'none' });
    setTimeout(() => wx.navigateBack(), 700);
  }
});
