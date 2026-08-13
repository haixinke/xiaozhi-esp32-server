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
const storyApi = require('../../utils/story-api');
const { INTERACTION_ICONS, SCENE_OPTIONS } = require('../../config/pre-hatch-assets');

const TOUCH_LINES = ['你碰到它啦。', '它轻轻晃了一下。', '它好像听见你了。', '蛋壳里传来小小的声音。'];
const SHARE_TITLE = '一起来养蛋宝宝吧';
// 早教班解锁门槛：领养满 24 小时（过第一天）后开放
const LEARN_UNLOCK_MIN_AGE_MS = 24 * 60 * 60 * 1000;
// 破壳后故事场景轮询频率：与后端故事状态定时任务（每 10 分钟）保持一致
const STORY_REFRESH_INTERVAL_MS = 10 * 60 * 1000;
// 窗户弹层只在“在家”大场景的“卧室”小场景出现（硬编码产品约定）
const STORY_WINDOW_BIG_SCENE = '在家';
const STORY_WINDOW_SMALL_SCENE = '卧室';
// 故事背景轨道宽 200vw，可横向拖拽查看左半屏；位移超过阈值才判定为拖拽，避免误伤点击
const STORY_DRAG_THRESHOLD_PX = 12;
// 左下角聊天入口 icon：按宠物原型选图，与静态项目同一组 96px 素材；
// 必须用 PNG：部分真机（尤其 iOS）image 组件无法解码 webp，icon 会空白
const CHAT_ENTRY_ICONS = {
  '玉兔': '/assets/ui/3d-actions/ui_3d_scene_find_home_jade_rabbit_96_v01.png',
  'YT': '/assets/ui/3d-actions/ui_3d_scene_find_home_jade_rabbit_96_v01.png',
  '锦鲤': '/assets/ui/3d-actions/ui_3d_scene_find_home_boon_koi_96_v01.png',
  'KOI': '/assets/ui/3d-actions/ui_3d_scene_find_home_boon_koi_96_v01.png'
};
const CHAT_ENTRY_ICON_FALLBACK = '/assets/ui/3d-actions/ui_3d_scene_find_home_egg_96_v01.png';

function chatEntryIcon(prototype) {
  return CHAT_ENTRY_ICONS[String(prototype || '')] || CHAT_ENTRY_ICON_FALLBACK;
}

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

// 临时测试：场景切换器的季节/时段中文映射
const SEASON_LABELS = { spring: '春季', summer: '夏季', autumn: '秋季', winter: '冬季' };
const PERIOD_LABELS = { day: '日间', sunset: '日落', night: '夜晚' };

// 临时测试：场景切换器选项（实时环境 + 36 组季节/天气/时段组合）。
// option 保留完整 scene 字段(season/weather/period/lightPhase/background/nest/egg)，override 时直接构造环境。
const SCENE_TESTER_OPTIONS = [{ key: 'auto', label: '实时环境' }].concat(
  SCENE_OPTIONS.map(o => Object.assign({}, o, {
    label: `${SEASON_LABELS[o.season] || o.season} · ${WEATHER_LABELS[o.weather] || o.weather} · ${PERIOD_LABELS[o.period] || o.period}`
  }))
);

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
    // 打开编辑器时传入的历史涂鸦操作序列(shell)，空数组表示空白开局
    doodleInitialOperations: [],
    // 每日窗景弹层数据
    dailyWindowVisible: false,
    dailyWindowOriginStyle: '',
    // 破壳后故事场景数据：来自故事引擎当前状态接口，10 分钟轮询
    storyImageUrl: '',
    storyTagImageUrl: '',
    storyWindowAvailable: false,
    storyWindowVisible: false,
    storyWindowOriginStyle: '',
    // 故事背景横向拖拽位移（px），轨道宽按图片真实宽高比自适应，范围 [屏宽-轨道宽, 0]
    storyScrollX: 0,
    // 故事轨道宽度样式（onStoryBgLoad 后按图片宽高比写入，空串时用 WXSS 的 200vw 兜底）
    storyTrackWidthPx: 0,
    storyTrackWidthStyle: '',
    // 窗户热区位置样式（随轨道宽按比例换算，空串时用 WXSS 的 200vw 比例兜底）
    storyWindowHotspotStyle: '',
    // 左下角聊天入口 icon（按原型选图）
    storyChatIcon: CHAT_ENTRY_ICON_FALLBACK,
    // 陪伴入口图标数据
    companionActions: [],
    wishUnlocked: true,
    learnUnlocked: false,
    // 命名弹层与左上角布局数据
    nameTopPx: 88,
    showNameSheet: false,
    nameDraft: '',
    nameCount: 0,
    nameError: '',
    savingName: false,
    // 临时测试：场景切换器数据
    sceneTesterOpen: false,
    sceneTesterKey: 'auto',
    sceneTesterLabel: '实时环境',
    sceneTesterOptions: SCENE_TESTER_OPTIONS
  },

  _navigating: false,
  // 临时测试：手动选中的场景 option；null 表示实时自动推导
  sceneTestOverride: null,

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
    // 每次进入 home 都允许因未命名自动弹一次命名框；本页内多次 renderPet 不重复弹
    this._namePromptShownForShow = false;
    this.configureLayoutMetrics();
    this.syncTabBar();
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

  // 拉取最新 DOODLE 记录的 artUrl；空值表示恢复环境蛋，旧请求不能覆盖新保存结果
  restoreDoodleArt(pet) {
    const requestToken = (this._doodleRestoreToken || 0) + 1;
    this._doodleRestoreToken = requestToken;
    if (!pet || !pet.id || petStore.getStage(pet) === 'hatched') {
      this._eggArtPetId = '';
      this.setData({ eggArtUrl: '' });
      return Promise.resolve();
    }
    const petId = String(pet.id);
    if (this.data.eggArtUrl && this._eggArtPetId !== petId) {
      this.setData({ eggArtUrl: '' });
    }
    return doodleApi.getLatestDoodleArtUrl(pet.id)
      .then((artUrl) => {
        const currentPet = this.data.pet;
        if (this._doodleRestoreToken !== requestToken || !currentPet || String(currentPet.id) !== petId) return;
        this._eggArtPetId = petId;
        this.setData({ eggArtUrl: typeof artUrl === 'string' ? artUrl : '' });
      })
      .catch(() => {});
  },

  // 左上角布局指标：名字药丸顶部对齐微信胶囊下沿；时钟位置由 incubation-scene 组件自行推导
  configureLayoutMetrics() {
    try {
      const windowInfo = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      const menuRect = wx.getMenuButtonBoundingClientRect ? wx.getMenuButtonBoundingClientRect() : null;
      const statusBarHeight = Number(windowInfo.statusBarHeight || 20);
      const nameTopPx = menuRect && Number(menuRect.bottom)
        ? Number(menuRect.bottom) + 8
        : statusBarHeight + 42;
      this.setData({ nameTopPx: Math.round(nameTopPx) });
    } catch (error) {
      this.setData({ nameTopPx: 88 });
    }
  },

  // 自定义悬浮 tabBar：破壳视频与命名弹层期间隐藏，其余时间显示当前为「蛋宝宝」
  syncTabBar() {
    if (typeof this.getTabBar !== 'function') return;
    const tabBar = this.getTabBar();
    // 孵化/命名弹层/窗景详情打开时隐藏悬浮「我的」齿轮，避免遮挡
    const hidden = !!(this.data.hatching || this.data.showNameSheet
      || this.data.dailyWindowVisible || this.data.storyWindowVisible);
    if (tabBar) tabBar.setData({ selected: 0, hidden });
  },

  onHide() {
    this.clearEnvironmentTimer();
    this.clearStoryTimer();
  },

  onUnload() {
    this.clearEnvironmentTimer();
    this.clearStoryTimer();
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
    const shouldRestoreDoodle = !this.data.pet;
    const previousPetId = this.data.pet && String(this.data.pet.id);
    try {
      const list = await get('/pet/list');
      this.setData({ petRestoreError: '' });
      if (Array.isArray(list) && list.length > 0) {
        const pet = petStore.savePetFromVO(list[0]);
        this.renderPet(pet);
        if (shouldRestoreDoodle || previousPetId !== String(pet.id)) this.restoreDoodleArt(pet);
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
      this.syncTabBar();
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
    const wishUnlocked = true;
    // 早教班过第一天(领养满24h)解锁：createdAt 缺失时视为已解锁，避免老数据被误锁
    const learnUnlocked = !pet.createdAt || (Date.now() - pet.createdAt) >= LEARN_UNLOCK_MIN_AGE_MS;
    this.finishPetRestore({
      pet: { ...pet, petType: pet.prototype },
      stage,
      petRestoreError: '',
      stageText: presentation.homeText,
      countdown: petStore.getCountdown(pet),
      dailyStatus: petStore.getDailyStatus(),
      actionLabel: presentation.actionLabel,
      storyChatIcon: chatEntryIcon(pet.prototype),
      wishUnlocked,
      learnUnlocked,
      companionActions: this.buildCompanionActions(wishUnlocked, learnUnlocked)
    });
    // 刷新破壳前环境
    this.refreshEnvironment();
    // 破壳后启动故事场景加载与 10 分钟轮询；破壳前清空
    this.refreshStoryState(stage);
    // 破壳前未命名时自动弹出命名框，引导用户给蛋宝宝起名
    this.maybePromptPetName(pet, stage);
  },

  // 破壳前且未命名时自动打开命名弹层；每次 onShow 只自动弹一次，用户手动点名字药丸仍可随时打开
  maybePromptPetName(pet, stage) {
    if (!pet || stage === 'hatched') return;
    if (this._namePromptShownForShow) return;
    if (this.data.hatching || this.data.showNameSheet) return;
    const name = (pet.name || '').trim();
    if (name) return;
    this._namePromptShownForShow = true;
    this.setData({ showNameSheet: true, nameDraft: '', nameCount: 0, nameError: '' });
    this.syncTabBar();
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
    // 临时测试：手动模式优先——用选中项的 OSS URL 构造环境，并暂停定时器避免到点被自动刷掉
    if (this.sceneTestOverride) {
      const o = this.sceneTestOverride;
      const environment = incubationEnv.resolveScene({
        sceneKey: o.key,
        season: o.season,
        weather: o.weather,
        period: o.period,
        lightPhase: o.lightPhase
      });
      this.setData({ environment });
      this.clearEnvironmentTimer();
      return;
    }
    const environment = incubationEnv.resolveForPet(this.data.pet, Date.now());
    this.setData({ environment });
    this.scheduleEnvironmentRefresh();
  },

  // 临时测试：展开/收起场景切换菜单（破壳前且非涂鸦打开时可用）
  onSceneTesterToggle() {
    if (this.data.doodleEditorVisible) return;
    this.setData({ sceneTesterOpen: !this.data.sceneTesterOpen });
  },

  // 临时测试：选择场景；'auto' 恢复实时自动推导，其余用手动 override 应用对应 OSS 场景
  onSceneTesterSelect(event) {
    const key = event && event.currentTarget && event.currentTarget.dataset && event.currentTarget.dataset.scene;
    const target = SCENE_TESTER_OPTIONS.find(item => item.key === key);
    if (!target) return;
    this.sceneTestOverride = key === 'auto' ? null : target;
    this.setData({
      sceneTesterKey: target.key,
      sceneTesterLabel: target.label,
      sceneTesterOpen: false
    });
    this.refreshEnvironment();
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

  // 破壳后故事场景：仅 hatched 阶段加载并启动 10 分钟轮询；其余阶段清空并停止
  refreshStoryState(stage) {
    if (stage !== 'hatched') {
      this.clearStoryTimer();
      if (this.data.storyImageUrl || this.data.storyTagImageUrl || this.data.storyWindowAvailable) {
        this.setData({
          storyImageUrl: '',
          storyTagImageUrl: '',
          storyWindowAvailable: false,
          storyWindowVisible: false
        });
      }
      return;
    }
    this.loadStoryState();
    this.scheduleStoryRefresh();
  },

  // 拉取故事引擎当前状态；失败静默保留旧场景，宠物切换或回退到破壳前时丢弃过期结果
  loadStoryState() {
    const pet = this.data.pet;
    if (!pet || !pet.id || this.data.stage !== 'hatched') return Promise.resolve();
    const petId = String(pet.id);
    return storyApi.getStoryState(pet.id)
      .then((state) => {
        const currentPet = this.data.pet;
        if (!currentPet || String(currentPet.id) !== petId || this.data.stage !== 'hatched') return;
        const imageUrl = state && typeof state.imageUrl === 'string' ? state.imageUrl : '';
        const tagImageUrl = state && typeof state.tagImageUrl === 'string' ? state.tagImageUrl : '';
        // 窗户弹层仅“在家”大场景 + “卧室”小场景 + 有窗景图时开放
        const windowAvailable = !!(state
          && state.bigSceneName === STORY_WINDOW_BIG_SCENE
          && state.smallSceneName === STORY_WINDOW_SMALL_SCENE
          && tagImageUrl);
        this.setData({
          storyImageUrl: imageUrl,
          storyTagImageUrl: tagImageUrl,
          storyWindowAvailable: windowAvailable,
          storyWindowVisible: windowAvailable ? this.data.storyWindowVisible : false
        });
      })
      .catch(() => {});
  },

  scheduleStoryRefresh() {
    this.clearStoryTimer();
    this.storyTimer = setInterval(() => this.loadStoryState(), STORY_REFRESH_INTERVAL_MS);
  },

  clearStoryTimer() {
    if (this.storyTimer) {
      clearInterval(this.storyTimer);
      this.storyTimer = null;
    }
  },

  // 点击卧室窗户热区：量出热区矩形作为弹层展开原点，打开窗景弹层
  onStoryWindowTap() {
    // 拖拽结束的 touchend 先于 tap 触发：本次手势是拖拽时不当作点窗户
    if (this._storyDragMoved) {
      this._storyDragMoved = false;
      return;
    }
    if (!this.data.storyWindowAvailable || !this.data.storyTagImageUrl || this.data.storyWindowVisible) return;
    const applyOrigin = (rect) => {
      const origin = rect || { left: 0, top: 0, width: 1, height: 1 };
      this.setData({
        storyWindowVisible: true,
        storyWindowOriginStyle: [
          `--daily-window-origin-left:${Number(origin.left || 0)}px;`,
          `--daily-window-origin-top:${Number(origin.top || 0)}px;`,
          `--daily-window-origin-width:${Math.max(1, Number(origin.width || 1))}px;`,
          `--daily-window-origin-height:${Math.max(1, Number(origin.height || 1))}px;`
        ].join('')
      });
      this.syncTabBar();
    };
    if (!wx.createSelectorQuery) {
      applyOrigin(null);
      return;
    }
    wx.createSelectorQuery().in(this).select('.story-window-hotspot').boundingClientRect(applyOrigin).exec();
  },

  onStoryWindowClosed() {
    this.setData({ storyWindowVisible: false });
    this.syncTabBar();
  },

  // 窗景图加载失败：重置 visible 触发组件重新加载
  onStoryWindowRetry() {
    if (!this.data.storyTagImageUrl) return;
    this.setData({ storyWindowVisible: false }, () => {
      this.setData({ storyWindowVisible: true });
      this.syncTabBar();
    });
  },

  // 故事背景横向拖拽：页面级手势，轨道 200vw 通过 transform 平移；
  // 位移超过阈值才进入拖拽，小幅移动不拦截，保证按钮与窗户热区的 tap 正常触发
  onStoryDragStart(event) {
    if (!this.data.storyImageUrl) return;
    const touch = event && event.touches && event.touches[0];
    if (!touch) return;
    this._storyDragMoved = false;
    this._storyDrag = { startX: Number(touch.clientX || 0), baseX: this.data.storyScrollX, moved: false };
  },

  onStoryDragMove(event) {
    const drag = this._storyDrag;
    if (!drag) return;
    const touch = event && event.touches && event.touches[0];
    if (!touch) return;
    const delta = Number(touch.clientX || 0) - drag.startX;
    if (!drag.moved && Math.abs(delta) < STORY_DRAG_THRESHOLD_PX) return;
    drag.moved = true;
    const maxShift = this._storyMaxScrollPx();
    const next = Math.max(-maxShift, Math.min(0, drag.baseX + delta));
    this.setData({ storyScrollX: next });
  },

  onStoryDragEnd() {
    if (this._storyDrag) this._storyDragMoved = this._storyDrag.moved;
    this._storyDrag = null;
  },

  // 视口尺寸惰性读取并缓存；windowHeight 缺失时兜底 667
  _storyViewport() {
    if (!this._storyViewportSize) {
      try {
        const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
        this._storyViewportSize = {
          width: Math.max(0, Number(info.windowWidth || 0)),
          height: Math.max(0, Number(info.windowHeight || 0)) || 667
        };
      } catch (error) {
        this._storyViewportSize = { width: 0, height: 667 };
      }
    }
    return this._storyViewportSize;
  },

  // 最大平移量 = 轨道宽 - 屏宽；图片未加载完成前按 200vw 兜底
  _storyMaxScrollPx() {
    const viewport = this._storyViewport();
    const trackWidth = this.data.storyTrackWidthPx || viewport.width * 2;
    return Math.max(0, trackWidth - viewport.width);
  },

  // 背景图加载完成：按真实宽高比铺满一屏高计算轨道宽，整张图任何区域都可拖拽到达
  onStoryBgLoad(event) {
    const detail = (event && event.detail) || {};
    const imageWidth = Number(detail.width || 0);
    const imageHeight = Number(detail.height || 0);
    if (!imageWidth || !imageHeight) return;
    const viewport = this._storyViewport();
    const trackWidth = Math.max(viewport.width, Math.round(viewport.height * imageWidth / imageHeight));
    if (trackWidth === this.data.storyTrackWidthPx) return;
    const maxShift = Math.max(0, trackWidth - viewport.width);
    // 窗户热区按轨道比例（right:3% width:19%）换算成像素，跟随轨道宽
    const hotspotWidth = Math.round(trackWidth * 0.19);
    const hotspotLeft = Math.round(trackWidth * 0.78);
    this.setData({
      storyTrackWidthPx: trackWidth,
      storyTrackWidthStyle: `width:${trackWidth}px;`,
      storyWindowHotspotStyle: `left:${hotspotLeft}px;width:${hotspotWidth}px;`,
      storyScrollX: Math.max(-maxShift, Math.min(0, this.data.storyScrollX))
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
    this.setData({
      dailyWindowVisible: true,
      dailyWindowOriginStyle: [
        `--daily-window-origin-left:${Number(rect.left || 0)}px;`,
        `--daily-window-origin-top:${Number(rect.top || 0)}px;`,
        `--daily-window-origin-width:${Math.max(1, Number(rect.width || 1))}px;`,
        `--daily-window-origin-height:${Math.max(1, Number(rect.height || 1))}px;`
      ].join('')
    });
    this.syncTabBar();
  },

  // 关闭每日窗景详情
  onDailyWindowClosed() {
    this.setData({ dailyWindowVisible: false });
    this.syncTabBar();
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
      this.syncTabBar();
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
    if (key === 'draw') {
      // 打开编辑器前读本地涂鸦操作缓存(shell)，恢复画布让用户在之前作品上继续编辑
      const pet = this.data.pet;
      const initialOperations = (pet && petStore.getDoodleShell(pet.id)) || [];
      this.setData({ doodleEditorVisible: true, doodleInitialOperations: initialOperations });
      return;
    }
    const routes = { wish: '/pages/wish/wish', learn: '/pages/lesson/lesson' };
    if (routes[key]) {
      // 300ms 场景过渡后跳转，与静态项目节奏一致
      setTimeout(() => wx.navigateTo({ url: routes[key] }), 300);
    }
  },

  // 编辑器只会在完整保存事务成功后通知 home；这里仅切换场景展示
  onDoodleSaved(e) {
    const detail = (e && e.detail) || {};
    const pet = this.data.pet;
    if (detail.ok !== true || typeof detail.artUrl !== 'string' || !pet
      || String(detail.petId) !== String(pet.id)) return;
    this._doodleRestoreToken = (this._doodleRestoreToken || 0) + 1;
    this._eggArtPetId = String(pet.id);
    this.setData({ eggArtUrl: detail.artUrl });
  },

  onDoodleEditorClose() { this.setData({ doodleEditorVisible: false }); },

  // 点击左上角名字药丸：打开命名弹层
  onPetNameTap() {
    if (!this.data.pet) return;
    const name = this.data.pet.name || '';
    this.setData({
      showNameSheet: true,
      nameDraft: name,
      nameCount: Array.from(name).length,
      nameError: ''
    });
    this.syncTabBar();
  },

  onNameInput(event) {
    const value = Array.from((event && event.detail && event.detail.value) || '').slice(0, 10).join('');
    this.setData({ nameDraft: value, nameCount: Array.from(value).length, nameError: '' });
  },

  // 保存昵称：孵化动作 NICKNAME + 兜底 PUT /pet/update 均由 pet-store 处理
  async onSaveName() {
    if (this.data.savingName || !this.data.pet) return;
    this.setData({ savingName: true, nameError: '' });
    const result = await petStore.updateNickname(this.data.nameDraft);
    this.setData({ savingName: false });
    if (!result.ok) {
      this.setData({ nameError: result.message || '名字没有保存成功，请重试' });
      return;
    }
    this.setData({
      showNameSheet: false,
      pet: { ...this.data.pet, name: result.pet ? result.pet.name : this.data.nameDraft }
    });
    this.syncTabBar();
    this.showFeedback(result.alreadyDone ? '名字改好啦。' : '我记住自己的名字啦。');
  },

  onCloseNameSheet() {
    if (this.data.savingName) return;
    this.setData({ showNameSheet: false, nameError: '' });
    this.syncTabBar();
  },

  doHatch() {
    if (this.data.hatching) return;
    this.setData({ hatching: true }, () => {
      this.syncTabBar();
      // 确保 video 组件已渲染后主动调 play
      this.hatchVideoCtx = wx.createVideoContext('hatchVideo', this);
      if (this.hatchVideoCtx) this.hatchVideoCtx.play();
    });
    // 同步发起破壳接口调用，视频播放结束后等待结果
    this._hatchPromise = petStore.createCollectionCard();
  },

  _finishHatch(result) {
    this.setData({ hatching: false });
    this.syncTabBar();
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

  onOpenLifeScene() {
    if (this.data.stage !== 'hatched') return;
    var sceneUrl = this.data.pet && this.data.pet.sceneUrl;
    if (!sceneUrl) return;
    var sceneKey = sceneConfig.getSceneKeyFromUrl(sceneUrl);
    wx.navigateTo({ url: '/pages/life-scene/life-scene?scene=' + sceneKey });
  }
});
