const petStore = require('../../utils/pet-store');
const auth = require('../../utils/auth');
const { get } = require('../../utils/request');
const wechatApi = require('../../utils/wechat-api');
const sceneConfig = require('../../utils/life-scenes');
const inviteApi = require('../../utils/invite-api');
const shareInvite = require('../../utils/share-invite');

const TOUCH_LINES = ['你碰到它啦。', '它轻轻晃了一下。', '它好像听见你了。', '蛋壳里传来小小的声音。'];
const SHARE_TITLE = '一起来养蛋宝宝吧';
const BASE_SHARE_PATH = '/pages/home/home?v=1&source=home_share';
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
    authChecked: false,
    petRestoreLoading: true,
    hatching: false,
    showPhoneAuthorization: false,
    authorizingPhone: false,
    hasPendingInvite: false,
    petRestoreError: ''
  },

  _navigating: false,
  
  onLoad() {
    // 同步检查登录态：未注册用户留在空白页(不渲染内容)，等 app 登录完成后再决定去向
    const cached = auth.getSession();
    if (cached && !auth.isExpired()) {
      // 本地有有效登录态，直接放行渲染
      this.setData({ authChecked: true });
      this.loadShareInviteCode();
      return;
    }
    // 本地无有效登录态，等待 app 层异步登录完成后再判断
    const app = getApp();
    if (app.globalData.authReady && typeof app.globalData.authReady.then === 'function') {
      app.globalData.authReady.then((session) => {
        if (session) {
          this.setData({ authChecked: true });
          this.loadShareInviteCode();
          this.onShow();
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
      // 后台静默刷新后端派生字段(今日心情等)，避免缓存跨天后一直展示旧状态
      this.loadPetFromServer();
      return;
    }
    // 冷启动:缓存空,从后端拉取已有蛋
    this._petRestoreFinished = false;
    this.setData({ pet: null, stage: 'empty', petRestoreLoading: true, petRestoreError: '' });
    this.loadPetFromServer();
  },

  async loadShareInviteCode() {
    this._shareInviteCode = null;
    try {
      const inviteCode = await inviteApi.getMine();
      if (this.isUsableShareInvite(inviteCode)) this._shareInviteCode = inviteCode.code;
      const pending = shareInvite.getPending();
      if (pending && inviteCode && pending.code === inviteCode.code) {
        shareInvite.clearPending();
        this.setData({ hasPendingInvite: false });
      }
    } catch (error) {
      this._shareInviteCode = null;
    }
  },

  isUsableShareInvite(inviteCode) {
    return !!(inviteCode && inviteCode.code && inviteCode.remaining > 0 && inviteCode.status === 1);
  },

  syncPendingInvite(pet) {
    const pending = shareInvite.getPending();
    if (pet && pending) shareInvite.clearPending();
    this.setData({ hasPendingInvite: !pet && !!pending });
  },

  onShareAppMessage() {
    const inviteCode = this._shareInviteCode;
    return {
      title: SHARE_TITLE,
      path: inviteCode ? `${BASE_SHARE_PATH}&inviteCode=${encodeURIComponent(inviteCode)}` : BASE_SHARE_PATH
    };
  },

  async loadPetFromServer() {
    try {
      const list = await get('/pet/list');
      this.setData({ petRestoreError: '' });
      if (Array.isArray(list) && list.length > 0) {
        this.renderPet(petStore.savePetFromVO(list[0]));
      } else {
        this.syncPendingInvite(null);
      }
    } catch (error) {
      // 拉取失败(未登录/网络异常)保持空态,不打扰用户
      if (!this.data.pet) {
        this.setData({ hasPendingInvite: false, petRestoreError: '宠物状态加载失败，请重试' });
      }
    } finally {
      if (!this._petRestoreFinished) this.finishPetRestore();
    }
  },

  finishPetRestore(data) {
    this._petRestoreFinished = true;
    this.setData({ ...(data || {}), petRestoreLoading: false }, () => {
      if (wx.showTabBar) wx.showTabBar({ animation: false });
    });
  },

  onRetryPetRestore() {
    if (this.data.petRestoreLoading) return;
    this._petRestoreFinished = false;
    this.setData({
      pet: null,
      stage: 'empty',
      petRestoreLoading: true,
      petRestoreError: '',
      hasPendingInvite: false
    });
    this.loadPetFromServer();
  },

  renderPet(pet) {
    if (!pet) {
      this.syncPendingInvite(null);
      this.finishPetRestore({ pet: null, stage: 'empty' });
      return;
    }
    this.syncPendingInvite(pet);
    const stage = petStore.getStage(pet);
    const presentation = petStore.getStagePresentation(stage);
    this.finishPetRestore({
      pet: { ...pet, petType: pet.prototype },
      stage,
      petRestoreError: '',
      stageText: presentation.homeText,
      countdown: petStore.getCountdown(pet),
      dailyStatus: petStore.getDailyStatus(),
      actionLabel: presentation.actionLabel
    });
  },

  onAddDevice() {
    const session = auth.getSession();
    if (session && !auth.isExpired() && session.hasPhone === true) {
      wx.navigateTo({ url: '/pages/add-device/add-device' });
      return;
    }
    this.setData({ showPhoneAuthorization: true });
  },

  onClosePhoneAuthorization() {
    if (!this.data.authorizingPhone) this.setData({ showPhoneAuthorization: false });
  },

  async onAuthorizePhone(event) {
    const phoneCode = event && event.detail && event.detail.code;
    if (!phoneCode) {
      wx.showToast({ title: '需要授权手机号后才能领取蛋宝宝', icon: 'none' });
      return;
    }
    if (this.data.authorizingPhone) return;
    this.setData({ authorizingPhone: true });
    try {
      const session = await getApp().ensureLogin();
      if (!session || !session.userId) throw new Error('invalid login session');
      await wechatApi.bindPhone(phoneCode);
      const boundSession = auth.markPhoneBound();
      if (!boundSession) throw new Error('invalid login session');
      getApp().applySession(boundSession);
      this.setData({ showPhoneAuthorization: false });
      wx.navigateTo({ url: '/pages/add-device/add-device' });
    } catch (error) {
      wx.showToast({ title: error.userMessage || '暂时无法连接服务，请稍后重试', icon: 'none' });
    } finally {
      this.setData({ authorizingPhone: false });
    }
  },

  showFeedback(text) {
    this.setData({ feedback: text });
    clearTimeout(this.feedbackTimer);
    this.feedbackTimer = setTimeout(() => this.setData({ feedback: '' }), 2200);
  },

  noop() {},

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
      this.doHatch();
    } else if (stage === 'hatched') {
      wx.navigateTo({ url: '/pages/chat/chat' });
    } else {
      wx.navigateTo({ url: '/pages/hatch-guide/hatch-guide' });
    }
  },

  doHatch() {
    if (this.data.hatching) return;
    wx.hideTabBar();
    this.setData({ hatching: true }, () => {
      // 确保 video 组件已渲染后主动调 play
      this.hatchVideoCtx = wx.createVideoContext('hatchVideo', this);
      if (this.hatchVideoCtx) this.hatchVideoCtx.play();
    });
    // 同步发起破壳接口调用，视频播放结束后等待结果
    this._hatchPromise = petStore.createCollectionCard();
  },

  _finishHatch(result) {
    wx.showTabBar();
    this.setData({ hatching: false });
    if (!result || !result.ok) {
      wx.showToast({ title: (result && result.message) || '破壳失败，请稍后重试', icon: 'none' });
      return;
    }
    this.onShow();
  },

  onHatchVideoEnded() {
    (async () => {
      try {
        const result = await this._hatchPromise;
        this._finishHatch(result);
      } catch (error) {
        this._finishHatch(null);
      }
    })();
  },

  onHatchVideoError() {
    // 视频加载失败时，退回正常状态并等待接口结果
    (async () => {
      try {
        const result = await this._hatchPromise;
        this._finishHatch(result);
      } catch (error) {
        this._finishHatch(null);
      }
    })();
  },

  onOpenProfile() {
    if (this.data.stage === 'hatched') wx.navigateTo({ url: '/pages/collection-card/collection-card?index=0' });
  },

  onOpenLifeScene() {
    if (this.data.stage !== 'hatched') return;
    var sceneUrl = this.data.pet && this.data.pet.sceneUrl;
    if (!sceneUrl) return;
    var sceneKey = sceneConfig.getSceneKeyFromUrl(sceneUrl);
    wx.navigateTo({ url: '/pages/life-scene/life-scene?scene=' + sceneKey });
  },

  async onChangeScene() {
    if (this._changingScene) return;
    this._changingScene = true;
    const result = await petStore.changeScene();
    this._changingScene = false;
    if (!result.ok) {
      this.showFeedback(result.message || '更换场景失败，请稍后重试');
      return;
    }
    this.setData({ 'pet.sceneUrl': result.sceneUrl });
    this.showFeedback('场景已更换');
  },

  onUnload() {
    clearTimeout(this.cuddleTimer);
    clearTimeout(this.feedbackTimer);
    clearInterval(this.cuddleTicker);
  }
});
