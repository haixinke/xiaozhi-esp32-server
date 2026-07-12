const petStore = require('../../utils/pet-store');
const petApi = require('../../utils/pet-api');

Page({
  data: { pet: null, tasks: [] },

  async onShow() {
    const pet = petStore.getPet();
    if (!pet) {
      wx.switchTab({ url: '/pages/home/home' });
      return;
    }
    // 非 demo：从后端拉修炼动作列表，缓存到 pet._hatchActions 供 getHatchActionState 派生
    if (!pet.demoMode && pet.hatchStatus !== 'HATCHED') {
      try {
        const actions = await petApi.listHatchActions(pet.id);
        pet._hatchActions = Array.isArray(actions) ? actions : [];
        petStore.savePet(pet);
      } catch (error) {
        // 拉取失败则沿用旧缓存或 tasks 默认值，不阻塞渲染
      }
    }
    const state = petStore.getHatchActionState(pet);
    this.setData({
      pet,
      tasks: [
        { key: 'nickname', title: '给蛋宝宝起昵称', desc: '让它知道自己是谁', reward: '提前 7 天', done: state.nicknameDone, route: '/pages/nickname/nickname' },
        { key: 'cuddle', title: '贴贴蛋宝宝', desc: '回首页长按蛋壳 3 秒', reward: '提前 1 小时 / 日', done: state.cuddleDone, route: 'home' },
        { key: 'wish', title: '今日许愿', desc: '告诉它你期待怎样的陪伴', reward: '提前 1 小时 / 日', done: state.wishDone, route: '/pages/wish/wish' },
        { key: 'lesson', title: '蛋前教育', desc: '今天想教它一件什么事', reward: '提前 1 小时 / 日', done: state.lessonDone, route: '/pages/lesson/lesson' },
        { key: 'doodle', title: '彩蛋涂鸦', desc: '为蛋壳选颜色和花纹', reward: '提前 12 小时', done: state.doodleDone, route: '/pages/doodle/doodle' }
      ]
    });
  },

  onTask(e) {
    const route = e.currentTarget.dataset.route;
    if (route === 'home') {
      wx.switchTab({ url: '/pages/home/home' });
      setTimeout(() => wx.showToast({ title: '长按蛋壳 3 秒完成贴贴', icon: 'none' }), 300);
      return;
    }
    wx.navigateTo({ url: route });
  }
});
