const petStore = require('../../utils/pet-store');
const listenApi = require('../../utils/listen-api');

// 收星音效：静态项目同款，反馈页播 +N 动画时同步播放
const SOUND = '/assets/scenes/lifecycle/post-hatch/40-interaction-fx/companion-star/companion-star-award.mp3';
// 收星动效总时长（入场 2s + 收尾），届时自动隐藏
const AWARD_DURATION_MS = 2100;
// 音效延迟到星星弹出节奏点播放
const SOUND_DELAY_MS = 520;
// 震动延迟到星星落定节奏点
const HAPTIC_DELAY_MS = 840;

// 业务码（后端 HTTP 200 + envelope.code，见 utils/listen-api.js 注释）
const CODE_ALL_ANSWERED = 10382;
const CODE_DAILY_LIMIT = 10384;

// 宠物形象图：与 UI 静态项目同款角色大立绘（按原型区分 stare 姿态）
const CHARACTER_ROOT = '/assets/scenes/lifecycle/post-hatch/30-character';
const CHARACTER_FOLDER_BY_PROTOTYPE = {
  '玉兔': 'jade-rabbit',
  'YT': 'jade-rabbit',
  '锦鲤': 'boon-koi',
  'KOI': 'boon-koi'
};
const CHARACTER_FOLDER_FALLBACK = 'jade-rabbit';

function resolvePetImage(pet) {
  const folder = CHARACTER_FOLDER_BY_PROTOTYPE[pet && pet.prototype] || CHARACTER_FOLDER_FALLBACK;
  return `${CHARACTER_ROOT}/${folder}/stare.webp`;
}

// 组内最后一题（选项 E「都不是」）的 optionKey，选中时自动展开补充文本框
const OTHER_OPTION_KEY = 'E';

// 反馈文案占位：进反馈页先显示（等 LLM 生成），拿到个性化文案后替换；
// 轮询窗口耗尽仍未拿到时回落到固定兜底文案。
const FEEDBACK_PENDING_TEXT = '正在听你说…';
const FEEDBACK_FALLBACK_TEXT = '谢谢你愿意说给我听。';
// 反馈轮询：提交成功后 LLM 异步生成，前端短轮询拉取
// 500ms × 8 ≈ 4s 窗口：对齐 LLM 典型生成耗时（1-3s），显著降低"轮询耗尽只见固定文案"的比例
const FEEDBACK_POLL_INTERVAL_MS = 500;
const FEEDBACK_POLL_MAX_ATTEMPTS = 8;

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
    this.pageActive = true;
    const pet = petStore.getPet();
    if (!pet || petStore.getStage(pet) !== 'hatched') {
      this.setData({ view: 'blocked', error: '破壳后才能来这里聊聊。' });
      return;
    }
    let reducedMotion = false;
    try {
      const system = wx.getSystemSetting ? wx.getSystemSetting() : wx.getSystemInfoSync();
      reducedMotion = Boolean(system.reducedMotion || system.enableReduceMotion);
    } catch (error) {}
    this.setData({
      petImage: resolvePetImage(pet),
      petName: pet.name || '蛋宝宝',
      reducedMotion
    });
    // 收集各题答案：{ [questionId]: { optionKey, customText } }，仅本次会话内存保留（中途退出不留草稿）
    this.answers = {};
    this.questions = [];
    this.groupId = null;
    this.drawGroup();
  },

  // 抽一组题：成功进入答题视图；10382/10384 分流到对应结束文案
  drawGroup() {
    this.setData({ view: 'loading', error: '' });
    listenApi.draw()
      .then((group) => {
        if (!group || !Array.isArray(group.questions) || !group.questions.length) {
          this.setData({ view: 'end', endTitle: '这些小事都听你说过啦', endText: '等有新的小事，我们再慢慢聊。' });
          return;
        }
        this.groupId = group.groupId;
        this.questions = group.questions;
        this.answers = {};
        this.setData({ groupName: group.groupName || '', questionTotal: group.questions.length });
        this.renderQuestion(0);
      })
      .catch((err) => {
        if (err && err.code === CODE_ALL_ANSWERED) {
          this.setData({ view: 'end', endTitle: '这些小事都听你说过啦', endText: '等有新的小事，我们再慢慢聊。', navTitle: '明天再来' });
        } else if (err && err.code === CODE_DAILY_LIMIT) {
          this.setData({ view: 'end', endTitle: '明天再来', endText: '今天先聊到这里。', navTitle: '明天再来' });
        } else {
          this.setData({ view: 'blocked', error: (err && err.userMessage) || '这个话题暂时没有新的问题。' });
        }
      });
  },

  renderQuestion(index) {
    const question = this.questions[index];
    const answer = this.answers[question.id] || {};
    const total = this.questions.length;
    this.setData({
      view: 'question',
      question,
      questionNumber: index + 1,
      progress: Math.round((index / total) * 100),
      selected: answer.optionKey || '',
      supplement: answer.customText || '',
      noteOpen: Boolean(answer.customText || answer.optionKey === OTHER_OPTION_KEY),
      scrollTarget: '',
      error: '',
      busy: false
    });
  },

  saveCurrent() {
    const question = this.data.question;
    if (!question) return;
    this.answers[question.id] = { optionKey: this.data.selected, customText: this.data.supplement };
  },

  onSelect(event) {
    if (this.data.busy) return;
    const key = event.currentTarget.dataset.key;
    if (!this.data.question.options.some((o) => o.key === key)) return;
    const isOther = key === OTHER_OPTION_KEY;
    this.setData({
      selected: key,
      error: '',
      noteOpen: this.data.noteOpen || isOther,
      scrollTarget: isOther ? 'listen-supplement' : ''
    });
    this.saveCurrent();
  },

  onSupplement(event) {
    if (this.data.busy) return;
    this.setData({ supplement: event.detail.value });
    this.saveCurrent();
  },

  onAddNote() {
    this.setData({ noteOpen: true, scrollTarget: 'listen-supplement' });
  },

  async onNext() {
    if (this.data.busy || !this.data.selected) return;
    this.saveCurrent();
    const index = this.data.questionNumber - 1;
    if (index < this.questions.length - 1) {
      this.renderQuestion(index + 1);
      return;
    }
    // 最后一题：整组提交
    this.setData({ busy: true, error: '' });
    try {
      const answers = this.questions.map((q) => {
        const a = this.answers[q.id] || {};
        return { questionId: q.id, optionKey: a.optionKey, customText: a.customText || undefined };
      });
      const result = await listenApi.submitAnswers(this.groupId, answers);
      const stars = result && Number.isFinite(Number(result.awardedStars)) ? Number(result.awardedStars) : 0;
      this.showFeedback(stars);
      // LLM 反馈文案异步生成：进反馈页后短轮询，拿到就替换固定文案；拿不到维持固定文案
      this.pollFeedback(this.groupId, 0);
    } catch (err) {
      this.setData({ busy: false, error: (err && err.userMessage) || '刚才没有保存好，请再试一次。' });
    }
  },

  showFeedback(awardedStars) {
    this.clearAward();
    const stars = Number(awardedStars || 0);
    this.setData({
      view: 'feedback',
      awardedStars: stars,
      awardVisible: stars > 0,
      // 先显示占位文案，LLM 文案轮询到后替换；窗口耗尽回落固定兜底文案
      feedbackText: FEEDBACK_PENDING_TEXT,
      error: '',
      busy: false
    });
    if (stars > 0) this.playAward();
  },

  // 轮询后端拿 LLM 生成的反馈文案；失败/未就绪继续等，窗口耗尽回落固定文案
  pollFeedback(groupId, attempt) {
    if (!this.pageActive) return;
    if (attempt >= FEEDBACK_POLL_MAX_ATTEMPTS) {
      // LLM 未在窗口内生成完成（慢/挂/未配置默认模型）：回落固定文案
      this.setData({ feedbackText: FEEDBACK_FALLBACK_TEXT });
      return;
    }
    listenApi.getFeedback(groupId)
      .then((res) => {
        const text = res && typeof res.feedback === 'string' ? res.feedback.trim() : '';
        if (text) {
          if (this.pageActive) this.setData({ feedbackText: text });
          return;
        }
        this.feedbackPollTimer = setTimeout(() => {
          if (this.pageActive) this.pollFeedback(groupId, attempt + 1);
        }, FEEDBACK_POLL_INTERVAL_MS);
      })
      .catch(() => {
        // 请求失败也继续尝试到窗口耗尽（最后一次失败自然走到 attempts 上限分支回落）
        this.feedbackPollTimer = setTimeout(() => {
          if (this.pageActive) this.pollFeedback(groupId, attempt + 1);
        }, FEEDBACK_POLL_INTERVAL_MS);
      });
  },

  playAward() {
    this.awardTimer = setTimeout(() => {
      if (this.pageActive) this.setData({ awardVisible: false });
    }, AWARD_DURATION_MS);
    try {
      if (wx.createInnerAudioContext) {
        this.audio = wx.createInnerAudioContext({ useWebAudioImplement: true });
        this.audio.src = SOUND;
        this.audio.volume = 0.2;
        this.audio.obeyMuteSwitch = true;
        if (this.audio.onError) this.audio.onError(() => this.releaseAudio());
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
    if (this.audio) {
      try { this.audio.stop(); this.audio.destroy(); } catch (error) {}
      this.audio = null;
    }
  },

  clearAward() {
    clearTimeout(this.awardTimer);
    clearTimeout(this.soundTimer);
    clearTimeout(this.hapticTimer);
    clearTimeout(this.feedbackPollTimer);
    this.releaseAudio();
    if (this.data.awardVisible) this.setData({ awardVisible: false });
  },

  onReturnRoom() {
    const pages = getCurrentPages();
    if (pages.length > 1) {
      wx.navigateBack({ fail: () => wx.reLaunch({ url: '/pages/home/home' }) });
    } else {
      wx.reLaunch({ url: '/pages/home/home' });
    }
  },

  onHide() {
    this.pageActive = false;
    this.clearAward();
  },

  onUnload() {
    this.onHide();
  }
});
