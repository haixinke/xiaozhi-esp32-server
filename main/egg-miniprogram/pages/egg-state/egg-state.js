/* 蛋形态详情（孵化中）
   破壳日固定、由后端下发；孵化进度由互动累积，仅影响成长表现，不改变破壳日。
   长按蛋 3 秒 = 今日「贴贴」（此处省略手势实现，见交互原型）。 */
Page({
  data: {
    pet: {
      id: 'd3', name: '神秘蛋',
      progress: 42,
      stateText: '它正在慢慢长大',
      countdownText: '约 6 天后破壳',
      mainBtnLabel: '孵化修炼手册',
      gradientFrom: '#EDE78E', gradientTo: '#9DB65B'
    }
  },

  onLoad(query) {
    // TODO: 按 query.id 拉取孵化状态
  },

  onMainButton() {
    // 到破壳日时改为进入破壳仪式：
    // wx.redirectTo({ url: `/pages/hatch/hatch?id=${this.data.pet.id}` });
    wx.showToast({ title: '打开孵化修炼手册', icon: 'none' });
  }
});
