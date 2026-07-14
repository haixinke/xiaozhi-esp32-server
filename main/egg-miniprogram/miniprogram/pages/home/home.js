const petStore = require('../../utils/pet-store');
const auth = require('../../utils/auth');
const { get } = require('../../utils/request');

const TOUCH_LINES = ['你碰到它啦。', '它轻轻晃了一下。', '它好像听见你了。', '蛋壳里传来小小的声音。'];
Page({
  data: {
    pet: null,
    stage: 'empty',
    stageText: '',
    countdown: '',
    dailyStatus: null,
    feedback: '',
    eggMotion: '',
    cuddleProgress: 0,
    actionLabel: '孵化修炼手册'
  },

  onLoad() {
    // 同步检查登录态：未注册用户直接重定向到欢迎页，不渲染首页内容
    const cached = auth.getSession();
    if (!cached || auth.isExpired() || !cached.hasPhone) {
      wx.reLaunch({ url: '/pages/welcome/welcome' });
    }
  },

  onShow() {
    const cached = petStore.getPet();
    if (cached) {
      this.renderPet(cached);
      return;
    }
    // 冷启动:缓存空,从后端拉取已有蛋(领养后缓存已有,不会走到这里)
    this.setData({ pet: null, stage: 'empty' });
    this.loadPetFromServer();
  },

  async loadPetFromServer() {
    try {
      const list = await get('/pet/list');
      if (Array.isArray(list) && list.length > 0) {
        this.renderPet(petStore.savePetFromVO(list[0]));
      }
    } catch (error) {
      // 拉取失败(未登录/网络异常)保持空态,不打扰用户
    }
  },

  renderPet(pet) {
    if (!pet) {
      this.setData({ pet: null, stage: 'empty' });
      return;
    }
    const stage = petStore.getStage(pet);
    const presentation = petStore.getStagePresentation(stage);
    this.setData({
      pet: { ...pet, petType: pet.prototype },
      stage,
      stageText: presentation.homeText,
      countdown: petStore.getCountdown(pet),
      dailyStatus: petStore.getDailyStatus(),
      actionLabel: presentation.actionLabel
    });
  },

  onAddDevice() {
    wx.navigateTo({ url: '/pages/add-device/add-device' });
  },

  onExhibitionDemo() {
    if (this.data.pet && this.data.pet.demoMode) {
      wx.navigateTo({ url: '/pages/exhibition-scenes/exhibition-scenes' });
      return;
    }
    wx.showModal({
      title: '进入展会快速体验',
      content: '将临时进入已破壳状态并体验六个生活场景。退出体验后，会恢复现在的孵化进度。',
      confirmText: '立即体验',
      confirmColor: '#002900',
      success: (result) => {
        if (!result.confirm) return;
        petStore.startExhibitionDemo();
        this.onShow();
        wx.navigateTo({ url: '/pages/exhibition-scenes/exhibition-scenes' });
      }
    });
  },

  onExitExhibition() {
    wx.showModal({
      title: '退出展会体验',
      content: '退出后将恢复进入体验前的蛋宝宝数据。',
      confirmColor: '#002900',
      success: (result) => {
        if (!result.confirm) return;
        petStore.endExhibitionDemo();
        this.onShow();
      }
    });
  },

  showFeedback(text) {
    this.setData({ feedback: text });
    clearTimeout(this.feedbackTimer);
    this.feedbackTimer = setTimeout(() => this.setData({ feedback: '' }), 2200);
  },

  onEggTap() {
    if (this.completedLongPress) {
      this.completedLongPress = false;
      return;
    }
    const now = Date.now();
    if (this.lastTapAt && now - this.lastTapAt < 2000) return;
    this.lastTapAt = now;
    petStore.recordTouch();
    this.setData({ eggMotion: 'egg--wobble' });
    this.showFeedback(TOUCH_LINES[Math.floor(Math.random() * TOUCH_LINES.length)]);
    if (wx.vibrateShort) wx.vibrateShort({ type: 'light' });
    setTimeout(() => this.setData({ eggMotion: '' }), 600);
  },

  onTouchStart() {
    if (!this.data.pet || this.data.stage === 'hatched') return;
    this.completedLongPress = false;
    const started = Date.now();
    this.setData({ eggMotion: 'egg--warming', cuddleProgress: 1 });
    this.cuddleTicker = setInterval(() => {
      const progress = Math.min(99, Math.round((Date.now() - started) / 30));
      this.setData({ cuddleProgress: progress });
    }, 90);
    this.cuddleTimer = setTimeout(() => {
      clearInterval(this.cuddleTicker);
      this.completedLongPress = true;
      this.setData({ cuddleProgress: 100, eggMotion: 'egg--warm' });
      if (wx.vibrateShort) wx.vibrateShort({ type: 'medium' });
      (async () => {
        const result = await petStore.completeCuddle();
        if (!result.ok) {
          this.showFeedback(result.message || '操作失败，请稍后重试');
          setTimeout(() => { this.setData({ cuddleProgress: 0, eggMotion: '' }); }, 900);
          return;
        }
        this.showFeedback(result.alreadyDone ? '它又往你这边靠了靠' : '它暖起来了');
        setTimeout(() => {
          this.setData({ cuddleProgress: 0, eggMotion: '' });
          this.onShow();
        }, 900);
      })();
    }, 3000);
  },

  onTouchEnd() {
    clearTimeout(this.cuddleTimer);
    clearInterval(this.cuddleTicker);
    if (!this.completedLongPress) this.setData({ cuddleProgress: 0, eggMotion: '' });
  },

  onPrimaryAction() {
    const stage = this.data.stage;
    if (stage === 'ready') {
      wx.navigateTo({ url: '/pages/hatch/hatch' });
    } else if (stage === 'hatched') {
      wx.navigateTo({ url: '/pages/chat/chat' });
    } else {
      wx.navigateTo({ url: '/pages/hatch-guide/hatch-guide' });
    }
  },

  onOpenProfile() {
    if (this.data.stage === 'hatched') wx.navigateTo({ url: '/pages/pet-detail/pet-detail' });
  },

  onUnload() {
    clearTimeout(this.cuddleTimer);
    clearTimeout(this.feedbackTimer);
    clearInterval(this.cuddleTicker);
  }
});
