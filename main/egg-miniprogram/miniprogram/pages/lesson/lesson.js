const petStore = require('../../utils/pet-store');
Page({
  data: { selected: '', options: [{ icon: '♡', value: '学会撒娇' }, { icon: '✦', value: '学会勇敢' }, { icon: '☺', value: '学会讲冷笑话' }] },
  onSelect(e) { this.setData({ selected: e.currentTarget.dataset.value }); },
  onSubmit() {
    if (!this.data.selected) return wx.showToast({ title: '先选一堂课吧', icon: 'none' });
    const result = petStore.completeLesson(this.data.selected);
    wx.showToast({ title: result.added ? '它认真听完了 · +5%' : '今天已经上过课啦', icon: 'none' });
    setTimeout(() => wx.navigateBack(), 700);
  }
});
