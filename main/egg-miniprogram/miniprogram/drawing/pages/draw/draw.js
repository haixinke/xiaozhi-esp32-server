// 一起画页面只协调画板与正式会话；星星结算以服务端结果为准。
const petStore = require('../../../utils/pet-store');
const api = require('../../utils/drawing-api');
const { createDrawingModel } = require('../../utils/drawing-model');
const { createDrawingSession } = require('../../utils/drawing-session');
const { createCanvasRenderer } = require('../../utils/drawing-canvas');
const { getReference } = require('../../utils/references');

const COLORS = ['#526B4D', '#D98652', '#5F8FA8', '#C97682', '#8573A3',
  '#AFC29A', '#E6CE73', '#9EC7D8', '#7B3E52', '#B9ABD2'];
const modal = options => new Promise(resolve => wx.showModal({ ...options,
  success: result => resolve(Boolean(result.confirm)), fail: () => resolve(false) }));
const definition = {
  data: {
    busy: false, started: false, peekVisible: false, result: null, animateReward: false,
    looksRemaining: 3, reference: null, errorText: '', notice: '', canvasReady: false,
    tool: 'brush', color: COLORS[0], width: 5, eraserWidth: 15, sticker: 'star',
    colors: COLORS, brushSizes: [2, 5, 8, 12, 18], eraserSizes: [6, 10, 15, 22, 30],
    stickers: [{ id: 'star', label: '星星', symbol: '✦' },
      { id: 'heart', label: '爱心', symbol: '♡' }, { id: 'leaf', label: '叶子', symbol: '⌁' }],
    toolPanelOpen: false, colorPickerOpen: false, canvasScale: 1, hasContent: false, canUndo: false,
    reducedMotion: false, assetError: false
  },
  async onLoad() {
    this.disposed = false;
    this.visible = true;
    this.model = createDrawingModel();
    this.renderer = createCanvasRenderer(this);
    const pet = petStore.getPetSnapshot().pet;
    if (!pet || !pet.id || pet.hatchStatus !== 'HATCHED') {
      this.setData({ errorText: '请先领取蛋宝宝，并切换到已破壳的蛋宝宝再来玩' });
      return;
    }
    this.petId = String(pet.id);
    this.session = createDrawingSession({ api, petId: this.petId,
      onChange: state => this.syncSession(state) });
    try {
      const info = wx.getSystemInfoSync ? wx.getSystemInfoSync() : {};
      this.setData({ reducedMotion: Boolean(info.reducedMotion || info.enableReduceMotion) });
    } catch (error) { /* 旧基础库无辅助功能字段时保留默认动效。 */ }
    await this.onRetry();
  },
  async onReady() {
    this.readyEvent = true;
    if (this.session) await this.onRetry();
  },
  async prepareCanvas() {
    if (this.data.canvasReady) return true;
    try {
      await this.renderer.init();
      if (this.disposed) return false;
      this.setData({ canvasReady: true });
      this.tryBeginPreview();
      return true;
    } catch (error) {
      if (!this.disposed) this.setData({ errorText: '画纸没有准备好，请点击重试' });
      return false;
    }
  },
  async onRetry() {
    if (!this.session || this.data.busy || this.disposed) return;
    if (!this.readyEvent) return;
    this.setData({ errorText: '', notice: '' });
    try {
      // 先准备可绘制的画纸，再开局占次，避免初始化失败浪费次数。
      if (!(await this.prepareCanvas())) return;
      // 已有一局时只重试素材，不再新开局。
      if (this.session.snapshot().session) {
        this.retryReference();
        return;
      }
      const pendingStart = this.session.snapshot().pendingStart;
      if (pendingStart) {
        await this.session.start();
        return;
      }
      const config = await this.session.load();
      if (this.disposed) return;
      // 配置在后台返回时保留恢复意图；前台再次显示后继续同一开局流程。
      if (!this.visible) { this.resumeNeedsStart = true; return; }
      if (config.dailyLimit > 0 && config.remainingPlays <= 0) {
        this.setData({ errorText: '今天的游玩次数已用完，明天再来吧' });
        return;
      }
      const pet = petStore.getPetSnapshot().pet;
      if (!pet || String(pet.id) !== this.petId || pet.hatchStatus !== 'HATCHED') {
        this.setData({ errorText: '当前蛋宝宝已变化，请返回后重新进入' }); return;
      }
      await this.session.start();
    } catch (error) {
      if (!this.disposed) this.setData({ errorText: error.userMessage || '暂时无法开始，请重试' });
    }
  },
  syncSession(state) {
    if (this.disposed) return;
    const reference = state.session ? getReference(state.session.referenceId) : null;
    this.setData({ busy: state.busy, started: Boolean(state.session),
      peekVisible: state.peekVisible, looksRemaining: state.looksRemaining,
      result: state.result, animateReward: state.animateReward, reference });
    if (state.session && !this.startedAt) this.startedAt = Date.now();
    if (state.session && !reference) this.setData({ errorText: '临摹素材暂不可用，请稍后重试' });
    if (state.result && state.animateReward && !this.rewardPlayed) {
      this.rewardPlayed = true;
      this.playReward();
    }
    this.syncExitAlert();
  },
  onReferenceLoad() { this.referenceLoaded = true; this.setData({ assetError: false }); this.tryBeginPreview(); },
  onReferenceError() { this.referenceLoaded = false; this.setData({ assetError: true, notice: '参考图加载失败，请点击重试' }); },
  retryReference() {
    const reference = this.data.reference;
    if (!reference) return;
    this.setData({ reference: null, assetError: false }, () => {
      if (!this.disposed) this.setData({ reference });
    });
  },
  tryBeginPreview() {
    if (this.session && this.data.canvasReady && this.referenceLoaded) this.session.beginPreview();
  },
  onPeek() {
    if (!this.session || this.locked()) return;
    this.model.end();
    this.session.peekAgain();
  },
  onDismissPeek() { if (this.session) this.session.dismissPeek(); },
  locked() {
    return !this.data.started || !this.data.canvasReady || !this.referenceLoaded
      || this.data.peekVisible || this.data.busy || Boolean(this.data.result) || !this.visible;
  },
  onTouchStart(event) {
    if (this.locked()) return;
    this.setData({ toolPanelOpen: false, colorPickerOpen: false, notice: '' });
    const touches = event.touches || [];
    if (touches.length >= 2) { this.beginPinch(touches); return; }
    if (this.pinching) return;
    const point = this.renderer.point(touches[0], this.data.canvasScale, Date.now() - this.startedAt);
    if (!point) return;
    if (!this.model.begin(this.data.tool, this.data.color,
      this.data.tool === 'eraser' ? this.data.eraserWidth : this.data.width, point, this.data.sticker)) {
      this.setData({ notice: '这一页画得很丰富了，试试点击「画好了」吧' }); return;
    }
    this.refreshCanvas();
  },
  onTouchMove(event) {
    if (this.locked()) return;
    const touches = event.touches || [];
    if (touches.length >= 2) {
      if (!this.pinch) this.beginPinch(touches);
      const distance = this.touchDistance(touches);
      if (this.pinch && distance) this.setData({ canvasScale: Math.max(1,
        Math.min(1.6, this.pinch.scale * distance / this.pinch.distance)) });
      return;
    }
    if (this.pinching || this.data.tool === 'sticker') return;
    const point = this.renderer.point(touches[0], this.data.canvasScale, Date.now() - this.startedAt);
    if (point) this.model.move(point);
    this.refreshCanvas();
  },
  onTouchEnd(event) {
    if (this.pinching) {
      if (!(event.touches || []).length) { this.pinching = false; this.pinch = null; }
      return;
    }
    this.model.end(); this.refreshCanvas();
  },
  onTouchCancel() { this.model.end(true); this.pinching = false; this.pinch = null; this.refreshCanvas(); },
  touchDistance(touches) {
    if (touches.length < 2) return 0;
    const a = touches[0]; const b = touches[1];
    return Math.hypot((a.clientX ?? a.x) - (b.clientX ?? b.x), (a.clientY ?? a.y) - (b.clientY ?? b.y));
  },
  beginPinch(touches) {
    this.model.end(true);
    this.pinching = true;
    this.pinch = { distance: this.touchDistance(touches), scale: this.data.canvasScale };
    this.refreshCanvas();
  },
  refreshCanvas() {
    if (!this.model || this.disposed) return;
    this.renderer.render(this.model.operations());
    const hasContent = this.model.hasContent();
    const canUndo = this.model.canUndo();
    if (hasContent !== this.data.hasContent || canUndo !== this.data.canUndo) this.setData({ hasContent, canUndo });
    this.syncExitAlert();
  },
  onUndo() { if (!this.locked()) { this.model.undo(); this.refreshCanvas(); } },
  onClear() { if (!this.locked()) { this.model.clear(); this.refreshCanvas(); } },
  onTool(event) {
    if (this.locked()) return;
    const tool = event.currentTarget.dataset.tool;
    this.setData({ tool, toolPanelOpen: tool !== this.data.tool || !this.data.toolPanelOpen, colorPickerOpen: false });
  },
  onColorPicker() { this.setData({ colorPickerOpen: !this.data.colorPickerOpen }); },
  onColor(event) { this.setData({ color: event.currentTarget.dataset.color, colorPickerOpen: false }); },
  onSize(event) {
    const value = Number(event.currentTarget.dataset.size);
    this.setData(this.data.tool === 'eraser' ? { eraserWidth: value } : { width: value });
  },
  onSticker(event) { this.setData({ sticker: event.currentTarget.dataset.sticker }); },
  async onFinish() {
    if (this.locked()) return;
    this.model.end();
    if (!this.model.evaluate().ok) {
      this.setData({ notice: '再画一会儿，让我看得清楚一点。' }); return;
    }
    this.setData({ notice: '', toolPanelOpen: false });
    try { await this.session.complete(); }
    catch (error) { if (!this.disposed) this.setData({ notice: error.userMessage || '提交没有成功，画还在，点「画好了」重试' }); }
  },
  playReward() {
    if (!this.visible || this.disposed) return;
    if (!this.data.reducedMotion && wx.vibrateShort) wx.vibrateShort({ type: 'light' });
    if (wx.createInnerAudioContext) {
      this.audio = wx.createInnerAudioContext();
      this.audio.src = '/assets/scenes/lifecycle/post-hatch/40-interaction-fx/companion-star/companion-star-award.mp3';
      this.audio.obeyMuteSwitch = true;
      this.audio.volume = 0.2;
      this.audio.onError(() => this.stopReward());
      this.audio.play();
    }
  },
  stopReward() { if (this.audio) { this.audio.destroy(); this.audio = null; } },
  syncExitAlert() {
    if (!wx.enableAlertBeforeUnload || !this.session) return;
    const state = this.session.snapshot();
    const shouldAlert = !state.result && this.data.hasContent;
    const message = '离开会丢失本局绘画进度';
    if (shouldAlert && !this.exitAlertEnabled) {
      wx.enableAlertBeforeUnload({ message }); this.exitAlertEnabled = true;
    } else if (!shouldAlert && this.exitAlertEnabled) {
      wx.disableAlertBeforeUnload(); this.exitAlertEnabled = false;
    }
  },
  async onBack() {
    if (this.data.busy || this.exiting) return;
    this.model.end();
    if (!this.data.result && this.model.hasContent()) {
      const content = '本局绘画进度会丢失，下次将重新开始。';
      const yes = await modal({ title: '离开一起画？', content, confirmText: '离开', cancelText: '继续画' });
      if (!yes || this.disposed) return;
    }
    this.leave();
  },
  onDone() { if (this.data.result) this.leave(); },
  leave() {
    this.exiting = true;
    if (wx.disableAlertBeforeUnload) wx.disableAlertBeforeUnload();
    if (getCurrentPages().length > 1) wx.navigateBack();
    else wx.switchTab({ url: '/pages/home/home' });
  },
  onHide() {
    this.visible = false;
    if (this.model) { this.model.end(); this.refreshCanvas(); }
    if (this.session) this.session.pause();
    this.stopReward();
  },
  onShow() {
    this.visible = true;
    if (this.session) this.session.resume();
    if (this.resumeNeedsStart) {
      this.resumeNeedsStart = false;
      return this.onRetry();
    }
  },
  onUnload() {
    this.disposed = true;
    if (this.session) this.session.dispose();
    if (this.renderer) this.renderer.dispose();
    this.stopReward();
    if (wx.disableAlertBeforeUnload) wx.disableAlertBeforeUnload();
  }
};

if (typeof Page === 'function') Page(definition);
module.exports = definition;
