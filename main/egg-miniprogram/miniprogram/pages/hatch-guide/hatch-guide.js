const petStore = require('../../utils/pet-store');

Page({
  data: { pet: null, tasks: [] },

  onShow() {
    const pet = petStore.getPet();
    if (!pet) {
      wx.switchTab({ url: '/pages/home/home' });
      return;
    }
    const today = petStore.todayKey();
    this.setData({
      pet,
      tasks: [
        { key: 'nickname', title: '给蛋宝宝起昵称', desc: '让它知道自己是谁', reward: '+20%', done: pet.tasks.nicknameDone, route: '/pages/nickname/nickname' },
        { key: 'cuddle', title: '贴贴蛋宝宝', desc: '回首页长按蛋壳 3 秒', reward: '+5% / 日', done: pet.tasks.cuddleDate === today, route: 'home' },
        { key: 'wish', title: '今日许愿', desc: '告诉它你期待怎样的陪伴', reward: '+5% / 日', done: pet.tasks.wishDate === today, route: '/pages/wish/wish' },
        { key: 'lesson', title: '蛋前教育', desc: '今天想教它一件什么事', reward: '+5% / 日', done: pet.tasks.lessonDate === today, route: '/pages/lesson/lesson' },
        { key: 'doodle', title: '彩蛋涂鸦', desc: '为蛋壳选颜色和花纹', reward: '+20%', done: pet.tasks.doodleDone, route: '/pages/doodle/doodle' }
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
