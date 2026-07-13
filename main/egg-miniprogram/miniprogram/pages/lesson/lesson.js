const petStore = require('../../utils/pet-store');
Page({
  data: { selected: '', options: [
    { icon: '🐠', value: '去深海潜水' },
    { icon: '☁️', value: '做个云朵棉花糖SPA' },
    { icon: '🌙', value: '月光牛奶浴' },
    { icon: '🎠', value: '坐星光旋转木马' },
    { icon: '🍃', value: '听树洞收音机' },
    { icon: '🍞', value: '在面包房打盹' }
  ] },
  onSelect(e) { this.setData({ selected: e.currentTarget.dataset.value }); },
  async onSubmit() {
    if (!this.data.selected) return wx.showToast({ title: '先选一堂课吧', icon: 'none' });
    const result = await petStore.completeLesson(this.data.selected);
    if (!result.ok) return wx.showToast({ title: result.message, icon: 'none' });
    wx.showToast({ title: result.alreadyDone ? '今天已经上过课啦' : '它认真听完了', icon: 'none' });
    setTimeout(() => wx.navigateBack(), 700);
  }
});
