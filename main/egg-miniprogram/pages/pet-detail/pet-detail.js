/* 宠物详情（已破壳）
   灵魂底色 7 维为破壳时一次性随机生成、不可更改；此处为静态示例数据。
   技能点击 → 底部弹层介绍（wx.showActionSheet 或自绘 popup，见交互原型）。 */
Page({
  data: {
    pet: {
      id: 'd1', name: '玉兔', prototype: '玉兔', style: '月白款',
      sealNo: '00214',
      summary: '温柔又爱胡思乱想，喜欢在夜里说悄悄话。',
      gradientFrom: '#EDE78E', gradientTo: '#F4B9AE',
      traits: [
        { key: 'curiosity', name: '好奇度', value: 72 },
        { key: 'introvert', name: '内向度', value: 84 },
        { key: 'nocturnal', name: '夜行性', value: 66 },
        { key: 'stability', name: '情绪稳态', value: 58 },
        { key: 'humor', name: '幽默感', value: 40 },
        { key: 'nostalgia', name: '念旧度', value: 77 },
        { key: 'fluidity', name: '流动性', value: 51 }
      ]
    },
    achievements: [
      { id: 'a1', name: '初次破壳', date: '2026-06-15' },
      { id: 'a2', name: '连续陪伴 7 天', date: '2026-06-22' },
      { id: 'a3', name: '第一次夜聊', date: '2026-06-18' }
    ],
    skills: [
      { id: 's1', name: '讲睡前故事' },
      { id: 's2', name: '陪你发呆' },
      { id: 's3', name: '记住纪念日' }
    ]
  },

  onLoad(query) {
    // TODO: 按 query.id 拉取灵魂底色 / 成就 / 技能
  },

  onTapSkill(e) {
    const id = e.currentTarget.dataset.id;
    const skill = this.data.skills.find(s => s.id === id);
    // TODO: 打开底部弹层展示技能介绍
    wx.showToast({ title: skill ? skill.name : '', icon: 'none' });
  }
});
