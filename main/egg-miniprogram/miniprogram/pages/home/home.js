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
    actionLabel: '孵化修炼手册',
    authChecked: false
  },

  _navigating: false,
  
  onLoad() {
    // 同步检查登录态：未注册用户留在空白页(不渲染内容)，等 app 登录完成后再决定去向
    const cached = auth.getSession();
    if (cached && !auth.isExpired() && cached.hasPhone) {
      // 本地有有效登录态，直接放行渲染
      this.setData({ authChecked: true });
      return;
    }
    // 本地无有效登录态，等待 app 层异步登录完成后再判断
    const app = getApp();
    if (app.globalData.authReady && typeof app.globalData.authReady.then === 'function') {
      app.globalData.authReady.then((session) => {
        if (session && session.hasPhone) {
          this.setData({ authChecked: true });
        } else if (!this._navigating) {
          this._navigating = true;
          wx.reLaunch({ url: '/pages/welcome/welcome' });
        }
      }).catch(() => {
        if (!this._navigating) {
          this._navigating = true;
          wx.reLaunch({ url: '/pages/welcome/welcome' });
        }
      });
    } else if (!this._navigating) {
      // authReady 不存在时兜底跳转
      this._navigating = true;
      wx.reLaunch({ url: '/pages/welcome/welcome' });
    }
  },

  onShow() {
    if (!this.data.authChecked) return;
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
