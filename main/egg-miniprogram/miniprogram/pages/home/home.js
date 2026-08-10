const petStore = require('../../utils/pet-store');
const auth = require('../../utils/auth');
const { get } = require('../../utils/request');
const wechatApi = require('../../utils/wechat-api');
const sceneConfig = require('../../utils/life-scenes');
const inviteApi = require('../../utils/invite-api');
const shareInvite = require('../../utils/share-invite');
const incubationEnv = require('../../utils/incubation-environment');
const envState = require('../../utils/environment-state');
const doodleApi = require('../../utils/doodle-api');
const { INTERACTION_ICONS } = require('../../config/pre-hatch-assets');

const TOUCH_LINES = ['你碰到它啦。', '它轻轻晃了一下。', '它好像听见你了。', '蛋壳里传来小小的声音。'];
const SHARE_TITLE = '一起来养蛋宝宝吧';

const COMPANION_ACTIONS = [
  { key: 'wish', title: '许愿池', icon: INTERACTION_ICONS.wish },
  { key: 'learn', title: '早教班', icon: INTERACTION_ICONS.learn },
  { key: 'draw', title: '画画', icon: INTERACTION_ICONS.draw }
];

const WEATHER_LABELS = {
  sunny: '晴朗',
  cloudy: '多云',
  rain: '下雨',
  storm: '雷雨',
  snow: '降雪',
  postSnow: '雪后'
};

function buildShareQuery(inviteCode) {
  return inviteCode
    ? `v=1&source=home_share&inviteCode=${encodeURIComponent(inviteCode)}`
    : 'v=1&source=home_share';
}

Page({
  data: {
    pet: null,
    stage: 'empty',
    stageText: '',
    countdown: '',
    dailyStatus: null,
    feedback: '',
    cuddleProgress: 0,
    actionLabel: '孵化修炼手册',
    authChecked: false,
    petRestoreLoading: true,
    hatching: false,
    showPhoneAuthorization: false,
    authorizingPhone: false,
    hasPendingInvite: false,
    petRestoreError: '',
    // 破壳前场景渲染数据
    environment: null,
    eggArtUrl: '',
    lampOn: false,
    doodleEditorVisible: false,
    // 每日窗景弹层数据
    dailyWindowVisible: false,
    dailyWindowOriginStyle: '',
    dailyWindowWeatherLabel: '',
    dailyWindowPeriodLabel: '',
    // 陪伴入口图标数据
    companionActions: [],
    wishUnlocked: true,
    learnUnlocked: false
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
      // 回显最新涂鸦画作到场景蛋壳的 art 层；失败静默，不阻塞主流程
      this.restoreDoodleArt(cached);
      return;
    }
    // 冷启动:缓存空,从后端拉取已有蛋
    this._petRestoreFinished = false;
    this.setData({ pet: null, stage: 'empty', petRestoreLoading: true, petRestoreError: '' });
    this.loadPetFromServer();
  },

  // 拉取最新 DOODLE 记录的 artUrl 回显到场景蛋壳；孵化期之外或失败时不处理
  restoreDoodleArt(pet) {
    if (!pet || !pet.id || petStore.getStage(pet) === 'hatched') return;
    doodleApi.getLatestDoodleArtUrl(pet.id)
      .then((artUrl) => { if (artUrl) this.setData({ eggArtUrl: artUrl }); })
      .catch(() => {});
  },

  onHide() {
    this.clearEnvironmentTimer();
  },

  onUnload() {
    this.clearEnvironmentTimer();
    clearTimeout(this.cuddleTimer);
    clearTimeout(this.feedbackTimer);
    clearInterval(this.cuddleTicker);
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
      path: `/pages/home/home?${buildShareQuery(inviteCode)}`
    };
  },

  onShareTimeline() {
    return {
      title: SHARE_TITLE
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
    const isHatched = stage === 'hatched';
    const wishUnlocked = true;
    const learnUnlocked = false;
    this.finishPetRestore({
      pet: { ...pet, petType: pet.prototype },
      stage,
      petRestoreError: '',
      stageText: presentation.homeText,
      countdown: petStore.getCountdown(pet),
      dailyStatus: petStore.getDailyStatus(),
      actionLabel: presentation.actionLabel,
      wishUnlocked,
      learnUnlocked,
      companionActions: this.buildCompanionActions(wishUnlocked, learnUnlocked)
    });
    // 刷新破壳前环境
    this.refreshEnvironment();
  },

  /**
   * 构建陪伴入口图标列表，附加解锁状态。
   * @param {boolean} wishUnlocked 许愿池是否已解锁
   * @param {boolean} learnUnlocked 早教班是否已解锁
   * @returns {Array<{key: string, title: string, icon: string, locked: boolean}>}
   */
  buildCompanionActions(wishUnlocked, learnUnlocked) {
    return COMPANION_ACTIONS.map((action) => ({
      ...action,
      locked: (action.key === 'wish' && !wishUnlocked) || (action.key === 'learn' && !learnUnlocked)
    }));
  },

  // 环境刷新：破壳前每次展示时计算当前场景，并按下一时段边界定时刷新
  refreshEnvironment() {
    if (!this.data.pet || this.data.stage === 'hatched') {
      this.setData({ environment: null });
      this.clearEnvironmentTimer();
      return;
    }
    const environment = incubationEnv.resolveForPet(this.data.pet, Date.now());
    this.setData({ environment });
    this.scheduleEnvironmentRefresh();
  },

  scheduleEnvironmentRefresh() {
    this.clearEnvironmentTimer();
    this.environmentTimer = setTimeout(
      () => this.refreshEnvironment(),
      envState.millisecondsUntilNextEnvironmentBoundary(Date.now())
    );
  },

  clearEnvironmentTimer() {
    if (this.environmentTimer) {
      clearTimeout(this.environmentTimer);
      this.environmentTimer = null;
    }
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

  // 轻触蛋：来自 incubation-scene 的 eggtap 事件
  onEggTap() {
    if (this.completedLongPress) {
      this.completedLongPress = false;
      return;
    }
    const now = Date.now();
    if (this.lastTapAt && now - this.lastTapAt < 2000) return;
    this.lastTapAt = now;
    petStore.recordTouch();
    this.showFeedback(TOUCH_LINES[Math.floor(Math.random() * TOUCH_LINES.length)]);
    if (wx.vibrateShort) wx.vibrateShort({ type: 'light' });
  },

  // 长按贴贴：来自 incubation-scene 的 eggcuddle 事件
  onEggCuddle() {
    if (!this.data.pet || this.data.stage === 'hatched') return;
    this.completedLongPress = false;
    const started = Date.now();
    this.setData({ cuddleProgress: 1 });
    this.cuddleTicker = setInterval(() => {
      const progress = Math.min(99, Math.round((Date.now() - started) / 30));
      this.setData({ cuddleProgress: progress });
    }, 90);
    this.cuddleTimer = setTimeout(() => {
      clearInterval(this.cuddleTicker);
      this.completedLongPress = true;
      this.setData({ cuddleProgress: 100 });
      if (wx.vibrateShort) wx.vibrateShort({ type: 'medium' });
      (async () => {
        const result = await petStore.completeCuddle();
        if (!result.ok) {
          this.showFeedback(result.message || '操作失败，请稍后重试');
          setTimeout(() => { this.setData({ cuddleProgress: 0 }); }, 900);
          return;
        }
        this.showFeedback(result.alreadyDone ? '它又往你这边靠了靠' : '它暖起来了');
        setTimeout(() => {
          this.setData({ cuddleProgress: 0 });
          this.onShow();
        }, 900);
      })();
    }, 3000);
  },

  // 台灯开关：由页面持有状态，避免组件内部回环
  onLampTap() {
    this.setData({ lampOn: !this.data.lampOn });
  },

  // 场景主图加载失败时点击重试
  onRetryScene() {
    this.refreshEnvironment();
  },

  // 点击窗户热区：打开每日窗景详情
  onWindowTap(e) {
    const environment = this.data.environment;
    if (!environment || !environment.windowImage) return;
    const rect = e && e.detail || {};
    const periodLabel = environment.lightPhase === 'sunset'
      ? '日落'
      : (environment.period === 'night' ? '夜晚' : '日间');
    this.setData({
      dailyWindowVisible: true,
      dailyWindowOriginStyle: [
        `--daily-window-origin-left:${Number(rect.left || 0)}px;`,
        `--daily-window-origin-top:${Number(rect.top || 0)}px;`,
        `--daily-window-origin-width:${Math.max(1, Number(rect.width || 1))}px;`,
        `--daily-window-origin-height:${Math.max(1, Number(rect.height || 1))}px;`
      ].join(''),
      dailyWindowWeatherLabel: WEATHER_LABELS[environment.weather] || '晴朗',
      dailyWindowPeriodLabel: periodLabel
    });
  },

  // 关闭每日窗景详情
  onDailyWindowClosed() {
    this.setData({ dailyWindowVisible: false });
  },

  // 窗景图片加载失败时重置 displayImage 触发重载
  onDailyWindowRetry() {
    const environment = this.data.environment;
    if (!environment || !environment.windowImage) return;
    this.setData({
      dailyWindowVisible: false,
      'environment.windowImage': ''
    }, () => {
      this.setData({
        dailyWindowVisible: true,
        'environment.windowImage': environment.windowImage
      });
    });
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

  /**
   * 点击陪伴入口图标：未解锁入口给出反馈，draw 为占位提示，其余按 300ms 场景过渡后跳转。
   * @param {WechatMiniProgramEvent} e 点击事件
   */
  onCompanionTap(e) {
    const key = e && e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.key;
    if (key === 'wish' && !this.data.wishUnlocked) return this.showFeedback('许愿池还在准备中。');
    if (key === 'learn' && !this.data.learnUnlocked) return this.showFeedback('蛋宝宝还没到早教的年龄，明天来试试吧。');
    if (key === 'draw') { this.setData({ doodleEditorVisible: true }); return; }
    const routes = { wish: '/pages/wish/wish', learn: '/pages/lesson/lesson' };
    if (routes[key]) {
      // 300ms 场景过渡后跳转，与静态项目节奏一致
      setTimeout(() => wx.navigateTo({ url: routes[key] }), 300);
    }
  },

  // 涂鸦编辑器导出画作后：先上传 OSS 拿 artUrl，再记录 DOODLE 动作；上传失败则保留画布可重试
  async onDoodleSaved(e) {
    const tempFilePath = e && e.detail && e.detail.tempFilePath;
    if (!tempFilePath) return;
    try {
      const artUrl = await doodleApi.uploadDoodleImage(tempFilePath);
      const result = await petStore.saveDoodle(artUrl);
      if (!result.ok) {
        wx.showToast({ title: result.message || '保存失败，请稍后重试', icon: 'none' });
        return;
      }
      this.setData({ eggArtUrl: artUrl, doodleEditorVisible: false });
      wx.showToast({ title: result.alreadyDone ? '蛋壳外观已更新' : '蛋壳变漂亮了', icon: 'none' });
    } catch (error) {
      wx.showToast({ title: (error && error.userMessage) || '画作没有保存好，请再试一次', icon: 'none' });
      // 编辑器保持打开，画布状态保留可重试
    }
  },

  onDoodleEditorClose() { this.setData({ doodleEditorVisible: false }); },

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
  }
});
