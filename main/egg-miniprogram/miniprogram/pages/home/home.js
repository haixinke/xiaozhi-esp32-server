const petStore = require('../../utils/pet-store');
const auth = require('../../utils/auth');
const wechatApi = require('../../utils/wechat-api');
const inviteApi = require('../../utils/invite-api');
const shareInvite = require('../../utils/share-invite');
const incubationEnv = require('../../utils/incubation-environment');
const envState = require('../../utils/environment-state');
const doodleApi = require('../../utils/doodle-api');
const storyApi = require('../../utils/story-api');
const starApi = require('../../utils/star-api');
const dailyNoteApi = require('../../utils/daily-note-api');
const { openDrawing } = require('../../utils/drawing-entry');
const remoteImage = require('../../utils/remote-image');
const petIcons = require('../../utils/pet-icons');
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

// caption 整串（多条用 | 或全角 ｜ 分隔）拆分为去空白、滤空段的池；背景与窗景共用
function splitCaptionPool(raw) {
  return raw.split(/[|｜]/).map((item) => item.trim()).filter((item) => item);
}
// 故事背景轨道宽 200vw，可横向拖拽查看左半屏。主轴判定：累计位移（勾股）超过该阈值前
// 横竖都不动；超过后按 |dx|>=|dy| 判主轴并锁死（45 度附近水平优先——误拖成本低、有边界
// 钳制兜底，垂直误触发代价是整屏渐隐换宠）。统一阈值取代旧的 6px 横拖进入阈值：垂直
// 手势从头到尾碰不到背景，无需回弹逻辑
const STORY_DRAG_THRESHOLD_PX = 10;
// 拖拽增益：手指位移放大倍数，让全景背景一次滑屏扫过更多轨道，体感更跟手
const STORY_DRAG_GAIN = 1.8;
// 松手惯性滚动：帧间隔 / 每帧速度衰减系数 / 最小启动速度(px/ms) / 停止速度(px/ms)；
// 松手前静止超过该窗口（ms）则不启动惯性，避免"停住后又滑出去"
const STORY_INERTIA_FRAME_MS = 16;
const STORY_INERTIA_DECAY = 0.94;
const STORY_INERTIA_MIN_VELOCITY = 0.08;
const STORY_INERTIA_STOP_VELOCITY = 0.02;
const STORY_INERTIA_STALE_MS = 80;
// 故事状态 caption 提示条：5000ms 展示 + 180ms 淡出
const STORY_CAPTION_TOAST_DURATION_MS = 5000;
const STORY_CAPTION_TOAST_FADE_MS = 180;
// caption 分钟轮换：后端下发整串配文（多条用 | 分隔），每 60s 本地随机抽一条展示
const STORY_CAPTION_ROTATE_INTERVAL_MS = 60 * 1000;
// 多宠物上下滑动切换：垂直位移超过该阈值且大于水平位移才判定为换宠，
// 避免与破壳后故事背景的横向拖拽、点按蛋等手势互相误触
const PET_SWIPE_THRESHOLD_PX = 60;
// 切换宠物的整屏渐隐过渡时长（淡入）；淡出阶段由预取就绪时点驱动，不再取固定时长
const PET_SWITCH_FADE_IN_MS = 240;
// 小纸条信封入口 icon 与抵达音效（与静态项目 life-scene 同素材；必须用 PNG：部分真机 image 无法解码 webp）
const DAILY_NOTE_ENVELOPE_ICON = '/assets/ui/3d-actions/ui_3d_scene_message_envelope_96_v01.png';
const DAILY_NOTE_LETTER_SOUND_SRC = '/assets/ui/3d-actions/letter-arrival.mp3';
// 抵达动效节奏：音效延迟 450ms 播放，纸飞机动画 1s，1050ms 统一收尾（与静态项目一致）
const DAILY_NOTE_SOUND_DELAY_MS = 450;
const DAILY_NOTE_ARRIVAL_DURATION_MS = 1050;

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
    // 多宠物列表：按领养时间正序（先领的在最上，index 0 为默认展示）；只有一只时与单宠物现状完全一致
    pets: [],
    currentPetIndex: 0,
    // 切换宠物时的整屏渐隐样式（空串表示无过渡）；只用 opacity——破壳后故事层是 fixed 定位，
    // transform 会让 fixed 元素改用该容器作为定位基准，破坏全屏布局
    petSwitchStyle: '',
    stage: 'empty',
    stageText: '',
    countdown: '',
    dailyStatus: null,
    // 星星罐余额（用户级）：仅破壳后加载展示；null 表示未加载/加载失败（隐藏星星行）
    starBalance: null,
    // 当日小纸条（DailyNoteTodayVO）；null 表示无内容/已打开/加载失败——入口位显示聊天 icon
    dailyNote: null,
    // 信封抵达动效进行中（纸飞机 + 信封淡入）；false 后不再重播，同日只播一次
    dailyNoteArriving: false,
    // 小纸条弹层显隐与本次实发星星（0/null 不展示 +N）
    dailyNoteVisible: false,
    dailyNoteAwardedStars: 0,
    // 弹层展示快照：打开成功后 dailyNote 清空（入口回聊天 icon），弹层内容须靠快照维持
    dailyNoteSnapshot: null,
    // 信封入口 icon
    dailyNoteEnvelopeIcon: DAILY_NOTE_ENVELOPE_ICON,
    // 打开接口进行中：防重复点击
    dailyNoteOpening: false,
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
    // 故事大场景是否为"在家"：仅控制左下角聊天 icon 显隐，小纸条信封在所有大场景都显示
    storyAtHome: false,
    // 故事背景横向拖拽位移（px），轨道宽按图片真实宽高比自适应，范围 [屏宽-轨道宽, 0]
    storyScrollX: 0,
    // 故事轨道宽度样式（onStoryBgLoad 后按图片宽高比写入，空串时用 WXSS 的 200vw 兜底）
    storyTrackWidthPx: 0,
    storyTrackWidthStyle: '',
    // 窗户热区位置样式（随轨道宽按比例换算，空串时用 WXSS 的 200vw 比例兜底）
    storyWindowHotspotStyle: '',
    // 左下角聊天入口 icon（按原型选图）
    storyChatIcon: '',
    // 故事状态 caption 提示条（toast）：进入/整串变化立即展示，之后每分钟轮换一条，每条展示 5000ms 后淡出
    storyCaptionToastText: '',
    storyCaptionToastVisible: false,
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
  // 进入主页后的首次服务端加载默认停在最先领取的宠物；首次加载成功后置 false，
  // 之后的静默刷新保持用户当前选中的宠物
  _initialPetLoad: true,
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
    this._iconPageInactive = false;
    // 每次进入 home 都允许因未命名自动弹一次命名框；本页内多次 renderPet 不重复弹
    this._namePromptShownForShow = false;
    this.configureLayoutMetrics();
    this.syncTabBar();
    const snapshot = petStore.getPetSnapshot();
    const cached = snapshot.pet;
    if (cached) {
      this.setData({ pets: snapshot.pets, currentPetIndex: snapshot.currentPetIndex });
      this.renderPet(cached);
      // 后台静默刷新后端派生字段(今日心情等)，避免缓存跨天后一直展示旧状态
      this.loadPetFromServer();
      // 回显最新涂鸦画作到场景蛋壳的 art 层；失败静默，不阻塞主流程
      this.restoreDoodleArt(cached);
      return;
    }
    // 冷启动:缓存空,从后端拉取已有蛋
    this._petRestoreFinished = false;
    this.setData({ pet: null, pets: [], currentPetIndex: 0, stage: 'empty', petRestoreLoading: true, petRestoreError: '' });
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
    // 孵化/命名弹层/窗景详情打开时隐藏百宝箱，避免遮挡
    const hidden = !!(this.data.hatching || this.data.showNameSheet
      || this.data.showPhoneAuthorization || this.data.dailyWindowVisible || this.data.storyWindowVisible);
    // 写真仅对当前已破壳宠物开放，首页每次切宠或状态变化都同步到百宝箱。
    const photoPetId = this.data.stage === 'hatched' && this.data.pet && this.data.pet.id
      ? String(this.data.pet.id) : '';
    if (tabBar) tabBar.setData({ selected: 0, hidden, photoPetId });
  },

  onHide() {
    // 下载仍可完成并存入缓存，但隐藏页面不接收过期回调。
    this._iconPageInactive = true;
    this._chatIconRequestId = (this._chatIconRequestId || 0) + 1;
    // 自定义 tabBar 不触发组件 pageLifetimes，由首页主动收起浮窗。
    const tabBar = typeof this.getTabBar === 'function' && this.getTabBar();
    if (tabBar) tabBar.setData({ toolboxVisible: false });
    this.clearEnvironmentTimer();
    this.clearStoryTimer();
    this.clearStoryCaptionRotation();
    this.clearStoryCaptionToast();
    this._stopStoryInertia();
    this.stopDailyNoteArrival();
  },

  onUnload() {
    this._iconPageInactive = true;
    this._chatIconRequestId = (this._chatIconRequestId || 0) + 1;
    this.clearEnvironmentTimer();
    this.clearStoryTimer();
    this.clearStoryCaptionRotation();
    this.clearStoryCaptionToast();
    this._stopStoryInertia();
    this.stopDailyNoteArrival();
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
      const snapshot = await petStore.refreshPets({ resetSelection: this._initialPetLoad });
      if (!snapshot) return;
      this._initialPetLoad = false;
      this.setData({ pets: snapshot.pets, currentPetIndex: snapshot.currentPetIndex, petRestoreError: '' });
      const hasHatched = snapshot.pets.some((pet) => pet.hatchStatus === 'HATCHED');
      const phoneRequired = this.syncPhoneAuthorization(snapshot.pets, true);
      this.loadStarBalance(hasHatched && !phoneRequired);
      this.loadDailyNote(hasHatched && !phoneRequired);
      this.renderPet(snapshot.pet);
      if (shouldRestoreDoodle || previousPetId !== (snapshot.pet && String(snapshot.pet.id))) {
        this.restoreDoodleArt(snapshot.pet);
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

  // 拉取星星罐余额：仅破壳后调用；失败静默降级为 null（首页星星行隐藏，不影响其他数据）
  loadStarBalance(shouldLoad) {
    if (!shouldLoad) {
      if (this.data.starBalance !== null) this.setData({ starBalance: null });
      return;
    }
    starApi.getBalance()
      .then((res) => {
        const balance = res && Number.isFinite(Number(res.balance)) ? Number(res.balance) : null;
        this.setData({ starBalance: balance });
      })
      .catch(() => {
        this.setData({ starBalance: null });
      });
  },

  // 拉取当日小纸条：仅破壳后调用；无内容/已打开/失败一律回退 null（入口位显示宠物站姿 icon，不打扰用户）
  loadDailyNote(shouldLoad) {
    if (!shouldLoad) {
      if (this.data.dailyNote !== null) this.setData({ dailyNote: null });
      return;
    }
    dailyNoteApi.getToday()
      .then((note) => {
        const hadNote = !!this.data.dailyNote;
        this.setData({ dailyNote: note || null });
        // 信封从无到有（当日首次进入首页）才播抵达动效；同日 onShow 重复拉取不重播
        if (!hadNote && note) this.playDailyNoteArrival();
      })
      .catch(() => {
        this.setData({ dailyNote: null });
      });
  },

  // 信封抵达动效：纸飞机动画 + 延迟播放音效，1050ms 统一收尾；同日只播一次（本地日期标记）
  playDailyNoteArrival() {
    const todayKey = this.dailyNoteLocalDate();
    if (this._dailyNoteArrivalDate === todayKey) return;
    this._dailyNoteArrivalDate = todayKey;
    this.setData({ dailyNoteArriving: true });
    try {
      if (typeof wx.createInnerAudioContext === 'function') {
        const audio = wx.createInnerAudioContext();
        audio.src = DAILY_NOTE_LETTER_SOUND_SRC;
        audio.volume = 0.25;
        audio.obeyMuteSwitch = true;
        this._dailyNoteAudio = audio;
        if (audio.onError) audio.onError(() => this.releaseDailyNoteSound());
        this._dailyNoteSoundTimer = setTimeout(() => {
          if (this._dailyNoteAudio) { try { this._dailyNoteAudio.play(); } catch (error) {} }
        }, DAILY_NOTE_SOUND_DELAY_MS);
      }
    } catch (error) {
      this.releaseDailyNoteSound();
    }
    this._dailyNoteArrivalTimer = setTimeout(() => {
      this.setData({ dailyNoteArriving: false });
      this.releaseDailyNoteSound();
    }, DAILY_NOTE_ARRIVAL_DURATION_MS);
  },

  // 本地日期键（yyyy-MM-dd）：仅用于"同日抵达动效只播一次"的客户端标记，业务日期以后端为准
  dailyNoteLocalDate() {
    const now = new Date();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${now.getFullYear()}-${month}-${day}`;
  },

  releaseDailyNoteSound() {
    if (!this._dailyNoteAudio) return;
    try { this._dailyNoteAudio.stop(); this._dailyNoteAudio.destroy(); } catch (error) {}
    this._dailyNoteAudio = null;
  },

  stopDailyNoteArrival() {
    clearTimeout(this._dailyNoteArrivalTimer);
    clearTimeout(this._dailyNoteSoundTimer);
    this.releaseDailyNoteSound();
    if (this.data.dailyNoteArriving) this.setData({ dailyNoteArriving: false });
  },

  // 点击信封：先弹层再调打开接口领奖；成功展示 +N 并刷新星星罐余额，失败 Toast 且保留红点可重试
  async onDailyNoteTap() {
    if (!this.data.dailyNote || this.data.dailyNoteOpening) return;
    this.setData({
      dailyNoteVisible: true,
      dailyNoteSnapshot: this.data.dailyNote,
      dailyNoteOpening: true,
      dailyNoteAwardedStars: 0
    });
    try {
      const result = await dailyNoteApi.markOpened();
      const amount = result && result.awarded && Number.isFinite(Number(result.amount)) ? Number(result.amount) : 0;
      this.setData({ dailyNoteAwardedStars: amount, dailyNoteOpening: false });
      // 打开成功当日入口回宠物站姿 icon：清空纸条，下次 onShow 拉取返回空维持该态
      this.setData({ dailyNote: null });
      this.loadStarBalance(true);
    } catch (error) {
      // 失败保留 dailyNote 与红点，用户可重试；弹层可关闭
      this.setData({ dailyNoteOpening: false });
      wx.showToast({ title: '打开失败，请重试', icon: 'none' });
    }
  },

  onDailyNoteClose() {
    this.setData({ dailyNoteVisible: false, dailyNoteAwardedStars: 0, dailyNoteSnapshot: null });
  },

  // 游戏邀请共用实际玩法页面；一起画只在当前宠物已破壳时关闭纸条并跳转。
  onDailyNoteGameTap() {
    const game = this.data.dailyNoteSnapshot && this.data.dailyNoteSnapshot.game;
    if (game && game.gameType === 'DRAW_TOGETHER') {
      openDrawing(petStore.getPetSnapshot().pet, 'letter', () => this.onDailyNoteClose());
      return;
    }
    if (game && game.gameType === 'LISTEN_TO_YOU') {
      this.onDailyNoteClose();
      wx.navigateTo({ url: '/pages/listen/listen' });
      return;
    }
    wx.showToast({ title: '敬请期待', icon: 'none' });
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
      pets: [],
      currentPetIndex: 0,
      stage: 'empty',
      petRestoreLoading: true,
      petRestoreError: '',
      hasPendingInvite: false
    });
    return this.loadPetFromServer();
  },

  renderPet(pet) {
    this.syncPhoneAuthorization(this.data.pets);
    const iconRequestId = (this._chatIconRequestId || 0) + 1;
    this._chatIconRequestId = iconRequestId;
    if (!pet) {
      this.syncPendingInvite(null);
      this.finishPetRestore({ pet: null, stage: 'empty', showNameSheet: false, storyChatIcon: '' });
      this.refreshEnvironment();
      this.refreshStoryState('empty');
      return;
    }
    this.syncPendingInvite(pet);
    const stage = petStore.getStage(pet);
    const iconUrl = petIcons.getPetIconUrl(pet.prototype);
    // 同原型刷新保留已加载图片；切换原型先清空，避免短暂显示上一只宠物。
    const storyChatIcon = this._chatIconUrl === iconUrl ? this.data.storyChatIcon : '';
    this._chatIconUrl = iconUrl;
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
      storyChatIcon: stage === 'hatched' ? storyChatIcon : '',
      wishUnlocked,
      learnUnlocked,
      companionActions: this.buildCompanionActions(wishUnlocked, learnUnlocked)
    });
    if (stage === 'hatched') {
      petIcons.loadPetIcon(pet.prototype, (localPath) => {
        const currentPet = this.data.pet;
        if (!localPath || this._iconPageInactive || this._chatIconRequestId !== iconRequestId || !currentPet
          || String(currentPet.id) !== String(pet.id) || this.data.stage !== 'hatched') return;
        this.setData({ storyChatIcon: localPath });
      });
    }
    // 刷新破壳前环境
    this.refreshEnvironment();
    // 破壳后启动故事场景加载与 10 分钟轮询；破壳前清空
    this.refreshStoryState(this.data.showPhoneAuthorization ? 'empty' : stage);
    // 破壳前未命名时自动弹出命名框，引导用户给蛋宝宝起名
    if (!this.data.showPhoneAuthorization) this.maybePromptPetName(pet, stage);
  },

  // 门槛属于账号：切到另一只孵化中的蛋，也不能绕过已经破壳后的绑定要求。
  syncPhoneAuthorization(pets, authoritative = false) {
    const session = auth.getSession();
    const app = getApp();
    // 只有本轮服务端完整列表才能解除旧门槛，本地旧快照不能证明破壳宠物已被替换。
    if (authoritative && !(pets || []).some((pet) => pet.hatchStatus === 'HATCHED')) {
      app.globalData.phoneBindingRequired = false;
    }
    const required = !!(app.globalData.phoneBindingRequired
      || (session && session.hasPhone !== true
        && (pets || []).some((pet) => pet.hatchStatus === 'HATCHED')));
    this.setData({ showPhoneAuthorization: required && !this.data.hatching });
    if (required) this.setData({ showNameSheet: false });
    this.syncTabBar();
    return required;
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
      this.clearStoryCaptionRotation();
      this.clearStoryCaptionToast();
      this.lastStoryCaption = '';
      this.storyCaptionPool = [];
      this.lastWindowCaption = '';
      this._pendingStoryImage = '';
      if (this.data.storyImageUrl || this.data.storyTagImageUrl || this.data.storyWindowAvailable || this.data.storyAtHome) {
        this.setData({
          storyImageUrl: '',
          storyTagImageUrl: '',
          storyWindowAvailable: false,
          storyWindowVisible: false,
          storyAtHome: false,
          windowCaptionPool: []
        });
      }
      return;
    }
    this.loadStoryState();
    this.scheduleStoryRefresh();
    // 从后台返回且轮换池仍在（整串未变）时：立即抽一条并重启分钟轮换；
    // 分钟轮换已在跑说明本次 onShow 已弹过（onShow 内 renderPet 会执行两次：缓存回显 + 服务端刷新），不重复弹
    if ((this.storyCaptionPool || []).length && !this.storyCaptionRotateTimer) {
      this.rotateStoryCaption();
      this.scheduleStoryCaptionRotation();
    }
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
          storyTagImageUrl: tagImageUrl,
          storyWindowAvailable: windowAvailable,
          storyWindowVisible: windowAvailable ? this.data.storyWindowVisible : false,
          // 聊天 icon 仅"在家"大场景显示；信封不受此限制。轮询失败静默保留旧值
          storyAtHome: !!(state && state.bigSceneName === STORY_WINDOW_BIG_SCENE)
        });
        // 故事背景走共享 downloadFile 通道：绕开 <image> 内置加载器挂起问题；成功才换图，失败保留旧背景
        this.resolveStoryBackground(petId, imageUrl);
        // 窗景文案池：镜像背景 caption 池的拆分逻辑（| / ｜ 分隔），整串变化时重建；
        // 仅建池供窗景组件每次开窗/点气泡随机抽，不引入分钟轮换
        const tagImageCaption = state && typeof state.tagImageCaption === 'string' ? state.tagImageCaption.trim() : '';
        if (tagImageCaption !== this.lastWindowCaption) {
          this.lastWindowCaption = tagImageCaption;
          this.setData({ windowCaptionPool: tagImageCaption ? splitCaptionPool(tagImageCaption) : [] });
        }
        // caption 整串变化（含首次进入）时重建轮换池并立即随机抽一条；
        // 10 分钟轮询到相同整串不重置池，分钟轮换继续
        const caption = state && typeof state.caption === 'string' ? state.caption.trim() : '';
        if (!caption) {
          if (this.lastStoryCaption) {
            this.lastStoryCaption = '';
            this.storyCaptionPool = [];
            this.clearStoryCaptionRotation();
          }
          return;
        }
        if (caption !== this.lastStoryCaption) {
          this.lastStoryCaption = caption;
          this.storyCaptionPool = splitCaptionPool(caption);
          this.lastCaptionIndex = -1;
          this.rotateStoryCaption();
          this.scheduleStoryCaptionRotation();
        }
      })
      .catch(() => {});
  },

  // 故事背景远程图下载：成功才替换 storyImageUrl，失败/宠物切换/过期结果静默保留旧背景
  resolveStoryBackground(petId, imageUrl) {
    this._pendingStoryImage = imageUrl;
    if (!imageUrl) {
      this.setData({ storyImageUrl: '' });
      return;
    }
    remoteImage.loadRemoteImage(imageUrl, (localPath) => {
      const currentPet = this.data.pet;
      if (!currentPet || String(currentPet.id) !== petId || this.data.stage !== 'hatched') return;
      if (this._pendingStoryImage !== imageUrl) return;
      // 下载失败保留旧背景，与轮询失败的静默策略一致
      if (!localPath) return;
      this.setData({ storyImageUrl: localPath });
    });
  },

  // caption 轮换：从池中随机抽一条弹 toast；多条时避免与上一条连续重复，单条重复弹
  rotateStoryCaption() {
    const pool = this.storyCaptionPool || [];
    if (!pool.length) return;
    let index = Math.floor(Math.random() * pool.length);
    while (pool.length > 1 && index === this.lastCaptionIndex) {
      index = Math.floor(Math.random() * pool.length);
    }
    this.lastCaptionIndex = index;
    this.showStoryCaptionToast(pool[index]);
  },

  // 分钟轮换定时器：每 60s 抽一条
  scheduleStoryCaptionRotation() {
    this.clearStoryCaptionRotation();
    this.storyCaptionRotateTimer = setInterval(() => this.rotateStoryCaption(), STORY_CAPTION_ROTATE_INTERVAL_MS);
  },

  clearStoryCaptionRotation() {
    clearInterval(this.storyCaptionRotateTimer);
    this.storyCaptionRotateTimer = null;
  },

  // 故事状态 caption toast：5000ms 展示后淡出
  showStoryCaptionToast(text) {
    this.clearStoryCaptionToast();
    this.setData({ storyCaptionToastText: text, storyCaptionToastVisible: false }, () => {
      this.setData({ storyCaptionToastVisible: true });
      this.storyCaptionToastTimer = setTimeout(() => {
        this.storyCaptionToastTimer = null;
        this.setData({ storyCaptionToastVisible: false });
        // 等淡出动画结束再卸载文本，避免文案一闪即没
        this.storyCaptionToastFadeTimer = setTimeout(() => {
          this.storyCaptionToastFadeTimer = null;
          this.setData({ storyCaptionToastText: '' });
        }, STORY_CAPTION_TOAST_FADE_MS);
      }, STORY_CAPTION_TOAST_DURATION_MS);
    });
  },

  clearStoryCaptionToast() {
    clearTimeout(this.storyCaptionToastTimer);
    clearTimeout(this.storyCaptionToastFadeTimer);
    this.storyCaptionToastTimer = null;
    this.storyCaptionToastFadeTimer = null;
    if (this.data.storyCaptionToastText || this.data.storyCaptionToastVisible) {
      this.setData({ storyCaptionToastText: '', storyCaptionToastVisible: false });
    }
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

  // 故事背景横向拖拽：页面级手势，轨道通过 transform 平移；
  // 位移超过阈值前横竖都不动（保证按钮与窗户热区的 tap 正常触发），超过后判主轴锁死
  onStoryDragStart(event) {
    if (!this.data.storyImageUrl) return;
    const touch = event && event.touches && event.touches[0];
    if (!touch) return;
    // 新拖拽开始时停掉未结束的惯性，避免惯性定时器与手势互相打架
    this._stopStoryInertia();
    this._storyDragMoved = false;
    this._storyDrag = {
      startX: Number(touch.clientX || 0),
      startY: Number(touch.clientY || 0),
      // 主轴：null 未判定；'horizontal' 背景拖拽；'vertical' 换宠手势（本处理器不再消费）
      axis: null,
      baseX: this.data.storyScrollX,
      moved: false,
      // 速度采样：最近一次的轨道位置与时间戳，供松手惯性计算初速度
      lastScrollX: this.data.storyScrollX,
      lastT: this._storyEventTime(event),
      velocity: 0
    };
  },

  onStoryDragMove(event) {
    const drag = this._storyDrag;
    if (!drag) return;
    const touch = event && event.touches && event.touches[0];
    if (!touch) return;
    const deltaX = Number(touch.clientX || 0) - drag.startX;
    const deltaY = Number(touch.clientY || 0) - drag.startY;
    if (!drag.axis) {
      // 主轴未判定：累计位移（水平+垂直合成）不足阈值时横竖都不动
      if (Math.hypot(deltaX, deltaY) < STORY_DRAG_THRESHOLD_PX) return;
      // 一次判死不反悔：45 度附近（|dx| >= |dy|）水平优先，否则锁垂直。
      // 锁垂直后本手势整段不再驱动背景——换宠判定交给 onPetSwipeEnd 按位移量裁决
      drag.axis = Math.abs(deltaX) >= Math.abs(deltaY) ? 'horizontal' : 'vertical';
    }
    if (drag.axis === 'vertical') return;
    drag.moved = true;
    const maxShift = this._storyMaxScrollPx();
    const next = Math.max(-maxShift, Math.min(0, drag.baseX + deltaX * STORY_DRAG_GAIN));
    // 采样瞬时速度（轨道位移/时间）；同毫秒的多条 move 事件不更新，避免除零
    const now = this._storyEventTime(event);
    if (now > drag.lastT) {
      drag.velocity = (next - drag.lastScrollX) / (now - drag.lastT);
      drag.lastScrollX = next;
      drag.lastT = now;
    }
    this.setData({ storyScrollX: next });
  },

  onStoryDragEnd(event) {
    const drag = this._storyDrag;
    if (!drag) return;
    this._storyDragMoved = drag.moved;
    // 主轴判定结果带出手势对象：onPetSwipeEnd 读取后才能让锁垂直的手势
    // 跳过终点 45 度校验（否则后期横向漂移追平 |dx|>=|dy| 会落进两不沾区）
    this._storyDragAxis = drag.axis;
    this._storyDrag = null;
    if (!drag.moved) return;
    // 松手前手指已停住一段时间，说明用户主动停稳，不启动惯性
    if (this._storyEventTime(event) - drag.lastT > STORY_INERTIA_STALE_MS) return;
    if (Math.abs(drag.velocity) < STORY_INERTIA_MIN_VELOCITY) return;
    this._startStoryInertia(drag.velocity);
  },

  // 事件时间戳：优先用 touch 事件自带 timeStamp，缺失时退回 Date.now（单测环境）
  _storyEventTime(event) {
    const t = event && event.timeStamp;
    return typeof t === 'number' && t > 0 ? t : Date.now();
  },

  // 松手惯性：按初速度逐帧衰减平移轨道，撞边界或速度低于阈值即停
  _startStoryInertia(velocity) {
    this._stopStoryInertia();
    let v = velocity;
    this._storyInertiaTimer = setInterval(() => {
      v *= STORY_INERTIA_DECAY;
      const maxShift = this._storyMaxScrollPx();
      const next = Math.max(-maxShift, Math.min(0, this.data.storyScrollX + v * STORY_INERTIA_FRAME_MS));
      this.setData({ storyScrollX: next });
      if (Math.abs(v) < STORY_INERTIA_STOP_VELOCITY || next === -maxShift || next === 0) {
        this._stopStoryInertia();
      }
    }, STORY_INERTIA_FRAME_MS);
  },

  _stopStoryInertia() {
    if (this._storyInertiaTimer) {
      clearInterval(this._storyInertiaTimer);
      this._storyInertiaTimer = null;
    }
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
    // 窗户热区按轨道比例（left:56% width:41%）换算成像素，跟随轨道宽；覆盖卧室背景图整扇飘窗
    const hotspotWidth = Math.round(trackWidth * 0.41);
    const hotspotLeft = Math.round(trackWidth * 0.56);
    this.setData({
      storyTrackWidthPx: trackWidth,
      storyTrackWidthStyle: `width:${trackWidth}px;`,
      storyWindowHotspotStyle: `left:${hotspotLeft}px;width:${hotspotWidth}px;`,
      // 初始居中：新轨道宽出现（首进或换到不同宽高比的图）时默认展示图片中间位置；
      // 同尺寸换图走上面的 early return，保留用户当前拖拽位置；不可拖时取 0（避免 -0）
      storyScrollX: maxShift > 0 ? -Math.round(maxShift / 2) : 0
    });
  },

  onAddDevice() {
    const session = auth.getSession();
    // 领取及孵化先体验，手机号绑定统一放到破壳成功之后。
    if (session && !auth.isExpired()) {
      wx.navigateTo({ url: '/pages/add-device/add-device' });
      return;
    }
    wx.showToast({ title: '登录状态已失效，请重新进入', icon: 'none' });
  },

  async onAuthorizePhone(event) {
    const phoneCode = event && event.detail && event.detail.code;
    if (!phoneCode) {
      wx.showToast({ title: '需要绑定手机号后才能继续使用', icon: 'none' });
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
      getApp().globalData.phoneBindingRequired = false;
      this.setData({ showPhoneAuthorization: false });
      this.onShow();
    } catch (error) {
      wx.showToast({ title: error.userMessage || '暂时无法连接服务，请稍后重试', icon: 'none' });
    } finally {
      this.setData({ authorizingPhone: false });
    }
  },

  onPhonePrivacy() {
    wx.navigateTo({ url: '/pages/privacy/privacy' });
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

  // 多宠物上下滑动切换：记录手势起点；只有多只宠物且无弹层/破壳视频时才允许切换，
  // 单宠物用户手势完全不生效（无滑动、无切换 UI）
  onPetSwipeStart(event) {
    // 上一次手势的拖拽标志（onStoryDragEnd 置位，供同手势 tap 抑制）不得跨手势残留，
    // 否则横向拖过故事背景一次后，后续所有垂直换宠滑动都被 onPetSwipeEnd 误拦——
    // 无论本次手势起点落在无 touch 绑定的 fixed story-track 还是 pet-view，touchstart
    // 冒泡到 .page 必先于 touchend 触发，此处清零是可靠复位点（主轴判定结果同理复位）
    this._storyDragMoved = false;
    this._storyDragAxis = null;
    if (!this.canSwitchPet()) return;
    const touch = event && event.touches && event.touches[0];
    if (!touch) return;
    this._petSwipe = { startX: Number(touch.clientX || 0), startY: Number(touch.clientY || 0) };
  },

  onPetSwipeEnd(event) {
    const swipe = this._petSwipe;
    this._petSwipe = null;
    if (!swipe || !this.canSwitchPet()) return;
    // 本次手势已被故事背景横向拖拽消费（子节点 touchend 先触发并置位）时不换宠
    if (this._storyDragMoved) return;
    const touch = event && event.changedTouches && event.changedTouches[0];
    if (!touch) return;
    const deltaX = Number(touch.clientX || 0) - swipe.startX;
    const deltaY = Number(touch.clientY || 0) - swipe.startY;
    // 主轴锁死语义（与 onStoryDragMove 判定一致，一次判死不反悔）：锁垂直的手势
    // 即使终点横向漂移追平 |dx|>=|dy| 也按换宠处理；无主轴记录（如孵化期无故事层）
    // 才退回终点整体位移的 45 度校验
    const lockedVertical = this._storyDragAxis === 'vertical';
    if (!lockedVertical && (Math.abs(deltaY) < PET_SWIPE_THRESHOLD_PX || Math.abs(deltaY) <= Math.abs(deltaX))) return;
    if (lockedVertical && Math.abs(deltaY) < PET_SWIPE_THRESHOLD_PX) return;
    const nextIndex = this.data.currentPetIndex + (deltaY < 0 ? 1 : -1);
    if (nextIndex < 0 || nextIndex >= this.data.pets.length) return;
    this.switchCurrentPet(nextIndex);
  },

  canSwitchPet() {
    if (this._petSwitching || this.data.pets.length < 2) return false;
    // 弹层与破壳视频打开期间不响应换宠，避免遮罩下手势穿透
    return !(this.data.showPhoneAuthorization || this.data.showNameSheet || this.data.doodleEditorVisible
      || this.data.dailyWindowVisible || this.data.storyWindowVisible || this.data.hatching);
  },

  // 点指示点换宠（方案D）：手势之外的保底切换入口。与手势换宠共用 canSwitchPet 闸门
  // （弹层打开不响应）与 switchCurrentPet 渐隐流程；点当前项幂等直接返回
  onPetDotTap(event) {
    if (!this.canSwitchPet()) return;
    const index = Number(event && event.currentTarget && event.currentTarget.dataset && event.currentTarget.dataset.index);
    if (!Number.isInteger(index) || index < 0 || index >= this.data.pets.length) return;
    if (index === this.data.currentPetIndex) return;
    this.switchCurrentPet(index);
  },

  // 切换当前宠物：整屏渐隐过渡（只用 opacity，不用 transform——破壳后故事层是 fixed 定位，
  // transform 会让 fixed 元素改用该容器作为定位基准，破坏全屏布局）。
  // 防闪屏：置换帧（卸载旧场景树/挂载新场景树）必须等新宠物首屏资产就绪——
  // 否则旧树已卸、新背景图未下载完成，opacity:1 时露出页面底色形成全屏闪白。
  // 预取通道复用 remoteImage 会话缓存：首次切换等待下载，切回再切立即命中。
  switchCurrentPet(index) {
    const target = this.data.pets[index];
    if (!target) return;
    // 选择意图立即生效；预取期间的刷新必须保留用户最新选择。
    const pet = petStore.selectPet(target.id);
    if (!pet) return;
    this._petSwitching = true;
    this.setData({ petSwitchStyle: 'opacity:0;' });
    this.prefetchPetAssets(pet).then(() => {
      const snapshot = petStore.getPetSnapshot();
      this.setData({ pets: snapshot.pets, currentPetIndex: snapshot.currentPetIndex, petSwitchStyle: 'opacity:1;' });
      this.renderPet(snapshot.pet);
      this.restoreDoodleArt(snapshot.pet);
      setTimeout(() => {
        this._petSwitching = false;
        this.setData({ petSwitchStyle: '' });
      }, PET_SWITCH_FADE_IN_MS);
    });
  },

  // 预取目标宠物切换后的首屏资产；600ms 超时兜底保证弱网不卡死切换（此时闪屏退化为原状）。
  // 破壳宠物取故事背景图（与 loadStoryState 同一数据源、同一下载通道）；
  // 孵化中宠物取环境场景图。失败也放行——renderPet 后续流程本身有静默重试。
  prefetchPetAssets(pet) {
    const stage = petStore.getStage(pet);
    if (stage === 'hatched') {
      // 仅预取背景图本体；tag 窗景图为次要元素，挂载慢不构成闪屏
      return storyApi.getStoryState(pet.id).then((state) => {
        const imageUrl = state && typeof state.imageUrl === 'string' ? state.imageUrl : '';
        if (!imageUrl) return;
        return new Promise((resolve) => remoteImage.loadRemoteImage(imageUrl, () => resolve()));
      }).catch(() => {});
    }
    const environment = incubationEnv.resolveForPet(pet, Date.now());
    const urls = [environment.fullSceneImage, environment.windowImage]
      .filter((url) => !!url);
    if (!urls.length) return Promise.resolve();
    return new Promise((resolve) => {
      let pending = urls.length;
      const done = () => { if (--pending <= 0) resolve(); };
      urls.forEach((url) => remoteImage.loadRemoteImage(url, done));
    });
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
    const snapshot = petStore.getPetSnapshot();
    this.setData({ showNameSheet: false, pets: snapshot.pets, currentPetIndex: snapshot.currentPetIndex });
    this.renderPet(snapshot.pet);
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
    // 只有视频收尾并确认破壳成功后才要求绑定，不能让接口先返回打断破壳体验。
    const session = auth.getSession();
    if (session && session.hasPhone !== true) getApp().globalData.phoneBindingRequired = true;
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
  }
});
