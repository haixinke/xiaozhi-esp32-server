const petStore = require('../../utils/pet-store');
const petIcons = require('../../utils/pet-icons');
const { createListenSession, FEEDBACK_PENDING_TEXT } = require('../../utils/listen-session');

// 收星音效：静态项目同款，反馈页播 +N 动画时同步播放
const SOUND = '/assets/scenes/lifecycle/post-hatch/40-interaction-fx/companion-star/companion-star-award.mp3';
// 收星动效总时长（入场 2s + 收尾），届时自动隐藏
const AWARD_DURATION_MS = 2100;
// 音效延迟到星星弹出节奏点播放
const SOUND_DELAY_MS = 520;
// 震动延迟到星星落定节奏点
const HAPTIC_DELAY_MS = 840;

Page({
  data: {
    view: 'loading', // loading | question | feedback | end | blocked
    navTitle: '听你说',
    groupName: '',
    question: null,          // 当前题 { id, text, options:[{key,text}] }
    questionNumber: 1,
    questionTotal: 0,
    progress: 0,
    selected: '',            // 当前选中的 optionKey
    supplement: '',          // 补充文本（选 E 或用户主动展开时收集）
    noteOpen: false,
    error: '',
    busy: false,
    petImage: '',
    petName: '',
    awardedStars: 0,
    awardVisible: false,
    feedbackText: FEEDBACK_PENDING_TEXT,
    endTitle: '',
    endText: '',
    scrollTop: 0,
    scrollTarget: '',
    reducedMotion: false
  },

  onLoad() {
    this.pageActive = false;
    this.sessionStarted = false;
    const pet = petStore.getPet();
    if (!pet || petStore.getStage(pet) !== 'hatched') {
      this.setData({ view: 'blocked', error: '破壳后才能来这里聊聊。' });
      return;
    }
    const user = petStore.getUser();
    this.sessionOwner = user && user.id;
    let reducedMotion = false;
    try {
      const system = wx.getSystemSetting ? wx.getSystemSetting() : wx.getSystemInfoSync();
      reducedMotion = Boolean(system.reducedMotion || system.enableReduceMotion);
    } catch (error) {}
    this.petIconPrototype = pet.prototype;
    this.setData({ petName: pet.name || '蛋宝宝', reducedMotion });
    this.session = createListenSession({
      onChange: (snapshot) => {
        if (!this.pageActive || !this.hasSessionOwner()) return;
        if (snapshot.view !== 'feedback') this.clearAward();
        this.setData(snapshot);
      },
      onAward: () => {
        if (this.pageActive && this.hasSessionOwner()) this.playAward();
      }
    });
  },

  hasSessionOwner() {
    const user = petStore.getUser();
    if ((user && user.id) === this.sessionOwner) return true;
    this.disposeSession();
    this.setData({ view: 'blocked', busy: false, awardVisible: false, error: '登录状态已变化，请返回房间后重试。' });
    return false;
  },

  onShow() {
    if (!this.session || !this.hasSessionOwner()) return;
    this.pageActive = true;
    // 只使用缓存工具返回的本地路径，避免 image 与下载接口重复拉取线上图片。
    const iconRequestId = (this.iconRequestId || 0) + 1;
    this.iconRequestId = iconRequestId;
    petIcons.loadPetIcon(this.petIconPrototype, (localPath) => {
      if (localPath && this.pageActive && this.session && this.iconRequestId === iconRequestId
        && this.hasSessionOwner()) this.setData({ petImage: localPath });
    });
    this.clearAward();
    this.session.resume();
    if (!this.sessionStarted) {
      this.sessionStarted = true;
      return this.session.drawGroup();
    }
  },

  drawGroup() {
    if (!this.pageActive || !this.session || !this.hasSessionOwner()) return;
    this.clearAward();
    return this.session.drawGroup();
  },

  onSelect(event) {
    if (this.pageActive && this.session && this.hasSessionOwner()) {
      this.session.select(event.currentTarget.dataset.key);
    }
  },

  onSupplement(event) {
    if (this.pageActive && this.session && this.hasSessionOwner()) this.session.supplement(event.detail.value);
  },

  onAddNote() {
    if (this.pageActive && this.session && this.hasSessionOwner()) this.session.addNote();
  },

  onNext() {
    if (this.pageActive && this.session && this.hasSessionOwner()) return this.session.next();
  },

  playAward() {
    if (!this.pageActive) return;
    this.clearAward();
    this.setData({ awardVisible: true });
    this.awardTimer = setTimeout(() => {
      if (this.pageActive) this.setData({ awardVisible: false });
    }, AWARD_DURATION_MS);
    try {
      if (wx.createInnerAudioContext) {
        const audio = wx.createInnerAudioContext({ useWebAudioImplement: true });
        this.audio = audio;
        this.audio.src = SOUND;
        this.audio.volume = 0.2;
        this.audio.obeyMuteSwitch = true;
        if (audio.onError) audio.onError(() => { if (this.audio === audio) this.releaseAudio(); });
        this.soundTimer = setTimeout(() => {
          if (this.pageActive && this.audio) { try { this.audio.play(); } catch (error) {} }
        }, SOUND_DELAY_MS);
      }
    } catch (error) {
      this.releaseAudio();
    }
    if (!this.data.reducedMotion) {
      this.hapticTimer = setTimeout(() => {
        if (this.pageActive && wx.vibrateShort) { try { wx.vibrateShort({ type: 'light' }); } catch (error) {} }
      }, HAPTIC_DELAY_MS);
    }
  },

  releaseAudio() {
    const audio = this.audio;
    this.audio = null;
    if (!audio) return;
    try { audio.stop(); } catch (error) {}
    try { audio.destroy(); } catch (error) {}
  },

  clearAward() {
    clearTimeout(this.awardTimer);
    clearTimeout(this.soundTimer);
    clearTimeout(this.hapticTimer);
    this.releaseAudio();
    if (this.pageActive && this.data.awardVisible) this.setData({ awardVisible: false });
  },

  disposeSession() {
    this.iconRequestId = (this.iconRequestId || 0) + 1;
    this.pageActive = false;
    this.clearAward();
    if (this.session) this.session.dispose();
    this.session = null;
  },

  onReturnRoom() {
    this.disposeSession();
    const pages = getCurrentPages();
    if (pages.length > 1) {
      wx.navigateBack({ fail: () => wx.reLaunch({ url: '/pages/home/home' }) });
    } else {
      wx.reLaunch({ url: '/pages/home/home' });
    }
  },

  onHide() {
    this.iconRequestId = (this.iconRequestId || 0) + 1;
    this.pageActive = false;
    if (this.session) this.session.pause();
    this.clearAward();
  },

  onUnload() {
    this.disposeSession();
  }
});
