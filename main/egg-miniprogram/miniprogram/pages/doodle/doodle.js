const petStore = require('../../utils/pet-store');
Page({
  data: {
    selectedColor: '#EDE78E', selectedColorName: '奶油白', selectedPattern: '星星',
    colors: [{ name: '奶油白', value: '#EDE78E' }, { name: '薄荷绿', value: '#BFD9C1' }, { name: '樱桃粉', value: '#F4B9AE' }, { name: '月亮蓝', value: '#B6CDE8' }],
    patterns: ['星星', '波点', '云朵', '裂纹', '爱心']
  },
  onLoad() {
    const pet = petStore.getPet();
    if (pet && pet.shell) this.setData({ selectedColor: pet.shell.color, selectedColorName: pet.shell.colorName, selectedPattern: pet.shell.pattern });
  },
  onColor(e) { this.setData({ selectedColor: e.currentTarget.dataset.value, selectedColorName: e.currentTarget.dataset.name }); },
  onPattern(e) { this.setData({ selectedPattern: e.currentTarget.dataset.value }); },
  async onSave() {
    const result = await petStore.saveDoodle(this.data.selectedColor, this.data.selectedColorName, this.data.selectedPattern);
    if (!result.ok) return wx.showToast({ title: result.message, icon: 'none' });
    wx.showToast({ title: result.alreadyDone ? '蛋壳外观已更新' : '蛋壳变漂亮了', icon: 'none' });
    setTimeout(() => wx.navigateBack(), 700);
  }
});
