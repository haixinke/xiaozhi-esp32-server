/* 破壳仪式
   真实触发时机：到达后端下发的固定破壳日 / 破壳事件，而非用户手动预览。
   阶段：confirm（物理装置已开）→ reveal（破壳 + 粒子 + 收藏卡生成）→ 落到 pet-detail。 */
Page({
  data: {
    phase: 'confirm',       // 'confirm' | 'reveal'
    pet: { id: 'd3', gradientFrom: '#EDE78E', gradientTo: '#9DB65B' },
    particles: []
  },

  onLoad(query) {
    // TODO: 按 query.id 拉取即将破壳的设备
    const colors = ['#9DB65B', '#EDE78E', '#F4B9AE'];
    const particles = [];
    for (let i = 0; i < 10; i++) {
      const angle = (i / 10) * Math.PI * 2;
      const radius = 156 + Math.random() * 48;   // rpx
      particles.push({
        i,
        tx: Math.round(Math.cos(angle) * radius) + 'rpx',
        ty: Math.round(Math.sin(angle) * radius) + 'rpx',
        color: colors[i % 3]
      });
    }
    this.setData({ particles });
  },

  onReveal() {
    this.setData({ phase: 'reveal' });
    // 收藏卡生成（后端返回款式 / 灵魂底色）后，落到宠物详情
    setTimeout(() => {
      wx.redirectTo({ url: `/pages/pet-detail/pet-detail?id=${this.data.pet.id}` });
    }, 1500);
  }
});
