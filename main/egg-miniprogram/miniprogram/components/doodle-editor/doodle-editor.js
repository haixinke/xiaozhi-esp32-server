// 画我的蛋壳编辑器：对齐静态 UI 项目的完整交互（画笔/橡皮/贴纸 + 捏合缩放 + 手动保存）。
const petStore = require('../../utils/pet-store');
const { saveArtwork } = require('../../utils/doodle-save');
const canvas = require('./doodle-canvas');
const { OSS_SCENE_BASE } = require('../../config/api');

const TOOL_BRUSH = 'brush';
const TOOL_ERASER = 'eraser';
const TOOL_STICKER = 'sticker';
const PREVIEW_RPX_RATIO = 2;
const PAGE_TRANSITION_MS = 320;
const CANVAS_NOTICE_DURATION = 1800;
const CANVAS_NOTICE_FADE_MS = 180;
// 编辑器底图与场景内蛋壳共用同一张蛋图，保证画完的笔触位置 1:1 回到房间
const EDITOR_BASE_IMAGE = `${OSS_SCENE_BASE}/egg/season-weather/spring_clear_day_egg_right45.webp`;

const TOOLBAR_ICONS = {
  brush: '/assets/ui/3d-actions/ui_3d_toolbar_brush_96_v02.png',
  eraser: '/assets/ui/3d-actions/ui_3d_toolbar_eraser_96_v02.png',
  sticker: '/assets/ui/3d-actions/ui_3d_toolbar_sticker_96_v02.png',
  // 本地资源统一 PNG，避免上传打包漏掉 webp 导致生产不显示
  undo: '/assets/ui/3d-actions/ui_3d_toolbar_undo_96_v02.png',
  undoDisabled: '/assets/ui/3d-actions/ui_3d_toolbar_undo_disabled_96_v01.png',
  clear: '/assets/ui/3d-actions/ui_3d_toolbar_clear_96_v01.png',
  clearDisabled: '/assets/ui/3d-actions/ui_3d_toolbar_clear_disabled_96_v01.png'
};

function buildSizeOptions(list) {
  return list.map(item => ({
    ...item,
    previewRpx: Math.max(6, item.pixels * PREVIEW_RPX_RATIO)
  }));
}

function touchDistance(touches) {
  if (!touches || touches.length < 2) return 0;
  const first = touches[0];
  const second = touches[1];
  const firstX = Number.isFinite(Number(first.clientX)) ? Number(first.clientX) : Number(first.x);
  const firstY = Number.isFinite(Number(first.clientY)) ? Number(first.clientY) : Number(first.y);
  const secondX = Number.isFinite(Number(second.clientX)) ? Number(second.clientX) : Number(second.x);
  const secondY = Number.isFinite(Number(second.clientY)) ? Number(second.clientY) : Number(second.y);
  if (![firstX, firstY, secondX, secondY].every(Number.isFinite)) return 0;
  return Math.hypot(secondX - firstX, secondY - firstY);
}

Component({
  properties: {
    visible: { type: Boolean, value: false },
    petId: { type: String, value: '' },
    // 打开编辑器时传入的历史涂鸦操作序列(shell)，用于恢复画布继续编辑；空数组表示空白开局
    initialOperations: { type: Array, value: [] }
  },

  data: {
    statusBarHeight: 20,
    toolbarIcons: TOOLBAR_ICONS,
    brushColors: canvas.BRUSH_COLORS,
    brushSizes: buildSizeOptions(canvas.BRUSH_SIZES),
    eraserSizes: buildSizeOptions(canvas.ERASER_SIZES),
    patterns: canvas.PATTERNS,
    selectedColor: canvas.DEFAULT_BRUSH_COLOR,
    selectedColorName: canvas.BRUSH_COLORS[0].name,
    selectedPattern: '',
    activeTool: TOOL_BRUSH,
    toolPanelOpen: false,
    colorPickerOpen: false,
    brushSizeIndex: 1,
    eraserSizeIndex: 2,
    canUndo: false,
    canClear: false,
    saving: false,
    saveStatus: 'saved',
    saveStatusText: '已保存',
    canvasReady: false,
    canvasScale: canvas.MIN_CANVAS_SCALE,
    canvasNoticeText: '',
    canvasNoticeTone: 'info',
    canvasNoticeVisible: false,
    exitConfirmVisible: false,
    exitConfirmSaving: false,
    exitConfirmErrorText: '',
    pageTransitionPhase: 'waiting'
  },

  lifetimes: {
    attached() {
      this.engine = null;
      this.pageActive = false;
      this.setupToken = 0;
      this.editRevision = 0;
      this.savedRevision = 0;
      this.savePromise = null;
      this.manualSaveTask = null;
      this.backInProgress = false;
      const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      this.setData({ statusBarHeight: info.statusBarHeight || 20 });
    },

    detached() {
      this.disposeEngine();
      this.pageActive = false;
      this.clearCanvasNoticeTimers();
      clearTimeout(this.pageTransitionTimer);
    }
  },

  observers: {
    'visible': function (visible) {
      if (visible) this.enterEditor();
      else this.exitEditor();
    }
  },

  methods: {
    /** 进入编辑器：初始化 canvas 引擎并播进入过渡 */
    enterEditor() {
      if (this.pageActive) return;
      this.pageActive = true;
      this.editRevision = 0;
      this.savedRevision = 0;
      this.savePromise = null;
      this.manualSaveTask = null;
      this.backInProgress = false;
      const pet = petStore.getPet();
      const shellColor = (pet && pet.shell && pet.shell.color) || '#EDE78E';
      this.setData({
        canvasReady: false,
        exitConfirmVisible: false,
        exitConfirmSaving: false,
        exitConfirmErrorText: '',
        pageTransitionPhase: 'waiting'
      });
      this.setSaveStatus('saved');
      this.initEngine(shellColor);
    },

    /** 退出编辑器：释放 canvas 层与计时器 */
    exitEditor() {
      this.pageActive = false;
      this.clearCanvasNoticeTimers();
      this.disposeEngine();
    },

    initEngine(shellColor) {
      this.disposeEngine();
      this.setupToken += 1;
      const token = this.setupToken;
      this.engine = canvas.createEngine({
        page: this,
        selectors: { base: '#eggBaseCanvas', art: '#eggArtCanvas' },
        baseImage: EDITOR_BASE_IMAGE,
        shellColor
      });
      this.engine.init().then(() => {
        if (token !== this.setupToken || !this.pageActive) return;
        // 有历史涂鸦操作则恢复画布，让用户在之前的作品上继续编辑
        const initial = this.data.initialOperations;
        if (Array.isArray(initial) && initial.length) {
          this.engine.restoreOperations(initial);
        }
        this.syncActionState();
        this.revealEditor();
      });
    },

    disposeEngine() {
      this.setupToken += 1;
      if (this.engine) {
        this.engine.dispose();
        this.engine = null;
      }
    },

    revealEditor() {
      const transitionDuration = this.pageTransitionDuration();
      this.setData({ canvasReady: true, pageTransitionPhase: 'entering' }, () => {
        clearTimeout(this.pageTransitionTimer);
        this.pageTransitionTimer = setTimeout(() => {
          this.pageTransitionTimer = null;
          if (this.pageActive) this.setData({ pageTransitionPhase: 'visible' });
        }, transitionDuration + 40);
      });
    },

    pageTransitionDuration() {
      try {
        const system = wx.getSystemSetting ? wx.getSystemSetting() : {};
        return system.reducedMotion || system.enableReduceMotion ? 20 : PAGE_TRANSITION_MS;
      } catch (error) {
        return PAGE_TRANSITION_MS;
      }
    },

    waitForPageExit() {
      clearTimeout(this.pageTransitionTimer);
      this.setData({ pageTransitionPhase: 'exiting' });
      return new Promise(resolve => {
        this.pageTransitionTimer = setTimeout(() => {
          this.pageTransitionTimer = null;
          resolve();
        }, this.pageTransitionDuration());
      });
    },

    /** 同步撤销/清空可用状态 */
    syncActionState() {
      this.setData({
        canUndo: this.engine ? this.engine.canUndo() : false,
        canClear: this.engine ? this.engine.canClear() : false
      });
    },

    /** 有未保存修改：只推进编辑版本，等待用户点击状态胶囊保存 */
    markDirty() {
      this.editRevision += 1;
      this.setSaveStatus('unsaved');
    },

    setSaveStatus(status) {
      const labels = { saved: '已保存', saving: '保存中…', unsaved: '保存', error: '重新保存' };
      this.setData({
        saving: status === 'saving',
        saveStatus: status,
        saveStatusText: labels[status] || labels.unsaved
      });
    },

    /** 执行一次完整保存事务；成功后按事务开始时的 revision 推进状态 */
    async persistCurrent() {
      if (!this.engine || !this.pageActive) return { ok: false };
      const targetRevision = this.editRevision;
      const operations = this.engine.getOperations();
      this.setSaveStatus('saving');
      const task = saveArtwork({
        petId: this.data.petId || (this.properties && this.properties.petId) || '',
        operations,
        exportArtwork: () => this.engine.exportArtwork()
      }).catch(error => ({
        ok: false,
        message: (error && error.message) || '画作没有保存好，请再试一次'
      }));
      this.savePromise = task;
      const result = await task;
      if (this.savePromise === task) this.savePromise = null;
      if (result.ok) {
        this.savedRevision = Math.max(this.savedRevision, targetRevision);
        this.triggerEvent('saved', result);
        if (this.editRevision === targetRevision) {
          this.setSaveStatus('saved');
        } else {
          this.setSaveStatus('unsaved');
        }
        return result;
      }
      this.saveErrorMessage = result.message;
      this.setSaveStatus('error');
      if (this.pageActive) {
        wx.showToast({ title: result.message || '画作没有保存好，请再试一次', icon: 'none' });
      }
      return result;
    },

    async onManualSave() {
      if (this.manualSaveTask) return this.manualSaveTask;
      if (this.engine && this.engine.endStroke()) {
        this.syncActionState();
        this.markDirty();
      }
      if (this.editRevision === this.savedRevision) {
        this.setSaveStatus('saved');
        return { ok: true };
      }
      this.dismissCanvasNotice();
      const task = this.persistCurrent();
      this.manualSaveTask = task;
      try {
        return await task;
      } finally {
        if (this.manualSaveTask === task) this.manualSaveTask = null;
      }
    },

    onContinueEditing() {
      if (this.backInProgress || this.data.exitConfirmSaving) return;
      this.setData({ exitConfirmVisible: false, exitConfirmErrorText: '' });
    },

    onExitConfirmTap() {
      this.onContinueEditing();
    },

    onExitConfirmDialogTap() {},

    async leaveEditor() {
      if (this.backInProgress) return;
      this.backInProgress = true;
      this.setData({ exitConfirmVisible: false });
      await this.waitForPageExit();
      this.triggerEvent('close');
    },

    async onSaveAndExit() {
      if (!this.data.exitConfirmVisible || this.backInProgress || this.data.exitConfirmSaving) return;
      this.setData({ exitConfirmSaving: true, exitConfirmErrorText: '' });
      const result = await this.onManualSave();
      if (result && result.ok && this.editRevision === this.savedRevision) {
        await this.leaveEditor();
        return;
      }
      this.setData({
        exitConfirmSaving: false,
        exitConfirmErrorText: (result && result.message) || '保存失败，请重试'
      });
    },

    /** 返回：已保存直接离开，未保存只展示放弃修改确认 */
    async onBack() {
      if (this.backInProgress || this.data.exitConfirmVisible) return;
      if (this.engine && this.engine.endStroke()) {
        this.syncActionState();
        this.markDirty();
      }
      if (this.manualSaveTask) {
        this.backInProgress = true;
        await this.manualSaveTask;
        this.backInProgress = false;
      }
      if (this.editRevision !== this.savedRevision) {
        this.collapseToolPanel();
        this.dismissCanvasNotice();
        this.setData({ exitConfirmVisible: true, exitConfirmSaving: false, exitConfirmErrorText: '' });
        return;
      }
      await this.leaveEditor();
    },

    /** 画布通知条（清空提示、贴纸未选提示） */
    clearCanvasNoticeTimers() {
      clearTimeout(this.canvasNoticeTimer);
      clearTimeout(this.canvasNoticeCleanupTimer);
      this.canvasNoticeTimer = null;
      this.canvasNoticeCleanupTimer = null;
    },

    dismissCanvasNotice() {
      this.clearCanvasNoticeTimers();
      if (!this.data.canvasNoticeText) return;
      this.setData({ canvasNoticeVisible: false });
      this.canvasNoticeCleanupTimer = setTimeout(() => {
        this.canvasNoticeCleanupTimer = null;
        if (!this.data.canvasNoticeVisible) this.setData({ canvasNoticeText: '' });
      }, CANVAS_NOTICE_FADE_MS);
    },

    showCanvasNotice(text, tone) {
      const message = String(text || '').trim();
      if (!message) return;
      this.clearCanvasNoticeTimers();
      this.setData({
        canvasNoticeText: message,
        canvasNoticeTone: tone === 'warning' ? 'warning' : 'info',
        canvasNoticeVisible: false
      }, () => {
        if (!this.pageActive) return;
        this.setData({ canvasNoticeVisible: true });
        this.canvasNoticeTimer = setTimeout(() => this.dismissCanvasNotice(), CANVAS_NOTICE_DURATION);
      });
    },

    collapseToolPanel() {
      if (!this.data.toolPanelOpen) return;
      this.setData({ toolPanelOpen: false, colorPickerOpen: false });
    },

    onCanvasBackdropTap() {
      this.collapseToolPanel();
    },

    onToolPanelTap() {},

    /** 切换画笔/橡皮/贴纸工具 */
    onTool(event) {
      const tool = event.currentTarget.dataset.tool;
      if (tool !== TOOL_BRUSH && tool !== TOOL_ERASER && tool !== TOOL_STICKER) return;
      this.setData({ activeTool: tool, toolPanelOpen: true, colorPickerOpen: false }, () => {
        if (tool === TOOL_BRUSH) this.cacheBrushSizeTrack();
      });
    },

    /** 选择贴纸类型：选中后点画布任意处放置 */
    onPattern(event) {
      const pattern = event.currentTarget.dataset.pattern;
      if (!canvas.PATTERNS.some(item => item.type === pattern)) return;
      this.setData({
        selectedPattern: pattern,
        activeTool: TOOL_STICKER,
        toolPanelOpen: true,
        colorPickerOpen: false
      });
    },

    onToggleBrushColorPicker() {
      if (this.data.activeTool !== TOOL_BRUSH) return;
      this.setData({ colorPickerOpen: !this.data.colorPickerOpen });
    },

    onColorPickerTap() {},

    onBrushColor(event) {
      const token = event.currentTarget.dataset.token;
      const color = canvas.BRUSH_COLORS.find(item => item.token === token);
      if (!color) return;
      this.setData({
        selectedColor: color.value,
        selectedColorName: color.name,
        activeTool: TOOL_BRUSH,
        colorPickerOpen: false
      });
    },

    selectBrushSize(index) {
      const safeIndex = Math.max(0, Math.min(canvas.BRUSH_SIZES.length - 1, Number(index) || 0));
      this.setData({ brushSizeIndex: safeIndex, colorPickerOpen: false });
    },

    onBrushSize(event) {
      this.selectBrushSize(Number(event.currentTarget.dataset.index));
    },

    /** 缓存粗细滑条的布局，供拖动定位 */
    cacheBrushSizeTrack() {
      if (!wx.createSelectorQuery) return;
      wx.nextTick(() => {
        wx.createSelectorQuery().in(this).select('#brushSizeTrack').boundingClientRect(rect => {
          this.brushSizeTrackRect = rect || null;
        }).exec();
      });
    },

    /** 在粗细滑条上拖动选择画笔粗细 */
    onBrushSizeScrub(event) {
      const touch = (event.touches || event.changedTouches || [])[0];
      const rect = this.brushSizeTrackRect;
      if (!touch || !rect || !rect.width) return;
      const clientX = Number(touch.clientX);
      if (!Number.isFinite(clientX)) return;
      const ratio = Math.max(0, Math.min(0.9999, (clientX - rect.left) / rect.width));
      this.selectBrushSize(Math.floor(ratio * canvas.BRUSH_SIZES.length));
    },

    selectEraserSize(index) {
      const safeIndex = Math.max(0, Math.min(canvas.ERASER_SIZES.length - 1, Number(index) || 0));
      this.setData({ eraserSizeIndex: safeIndex });
    },

    onEraserSize(event) {
      this.selectEraserSize(Number(event.currentTarget.dataset.index));
    },

    onUndo() {
      this.collapseToolPanel();
      if (!this.engine || !this.data.canUndo) return;
      this.dismissCanvasNotice();
      this.engine.undo();
      this.syncActionState();
      this.markDirty();
    },

    onClear() {
      this.collapseToolPanel();
      if (!this.engine || !this.data.canClear) return;
      this.engine.clear();
      this.syncActionState();
      this.markDirty();
    },

    /** 当前工具的笔刷描述（传给引擎） */
    currentBrush() {
      const isEraser = this.data.activeTool === TOOL_ERASER;
      const options = isEraser ? canvas.ERASER_SIZES : canvas.BRUSH_SIZES;
      const index = isEraser ? this.data.eraserSizeIndex : this.data.brushSizeIndex;
      return {
        tool: isEraser ? TOOL_ERASER : TOOL_BRUSH,
        color: this.data.selectedColor,
        size: (options[index] || options[1]).pixels
      };
    },

    /** 把触摸点换算成画布 0-1 归一化坐标（考虑缩放） */
    canvasPoint(event) {
      const touch = (event.touches || event.changedTouches || [])[0];
      if (!touch || !this.engine) return null;
      const size = this.engine.canvasSize();
      if (!size) return null;
      const scale = Math.max(canvas.MIN_CANVAS_SCALE, Number(this.data.canvasScale) || 1);
      const layer = this.engine.layerRect();
      const clientX = Number.isFinite(Number(touch.clientX)) ? Number(touch.clientX) : Number(touch.x);
      const clientY = Number.isFinite(Number(touch.clientY)) ? Number(touch.clientY) : Number(touch.y);
      if (!Number.isFinite(clientX) || !Number.isFinite(clientY)) return null;
      const scaledWidth = size * scale;
      const scaledLeft = layer.left - (scaledWidth - size) / 2;
      const scaledTop = layer.top - (scaledWidth - size) / 2;
      return {
        x: Math.max(0, Math.min(1, (clientX - scaledLeft) / scaledWidth)),
        y: Math.max(0, Math.min(1, (clientY - scaledTop) / scaledWidth))
      };
    },

    /** 双指捏合开始：取消进行中的笔画 */
    beginPinch(touches) {
      const distance = touchDistance(touches);
      if (!distance) return;
      if (this.engine) this.engine.cancelStroke();
      this.syncActionState();
      this.pinchGesture = {
        startDistance: distance,
        startScale: Math.max(canvas.MIN_CANVAS_SCALE, Number(this.data.canvasScale) || 1)
      };
      this.suppressDrawingUntilRelease = true;
    },

    updatePinch(touches) {
      if (!this.pinchGesture) this.beginPinch(touches);
      if (!this.pinchGesture) return;
      const distance = touchDistance(touches);
      if (!distance) return;
      const nextScale = Math.max(
        canvas.MIN_CANVAS_SCALE,
        Math.min(canvas.MAX_CANVAS_SCALE, this.pinchGesture.startScale * distance / this.pinchGesture.startDistance)
      );
      this.setData({ canvasScale: Number(nextScale.toFixed(3)) });
    },

    finishPinchIfReleased(event) {
      if (!this.pinchGesture && !this.suppressDrawingUntilRelease) return false;
      const remainingTouches = (event.touches || []).length;
      if (remainingTouches < 2) this.pinchGesture = null;
      if (remainingTouches === 0) this.suppressDrawingUntilRelease = false;
      return true;
    },

    onCanvasTouchStart(event) {
      this.collapseToolPanel();
      if (!this.engine) return;
      const touches = event.touches || [];
      if (touches.length >= 2) {
        this.beginPinch(touches);
        return;
      }
      if (this.suppressDrawingUntilRelease) return;
      const point = this.canvasPoint(event);
      if (!point) return;
      if (this.data.activeTool === TOOL_STICKER) {
        if (!this.data.selectedPattern) {
          this.showCanvasNotice('先选择一种贴纸', 'warning');
          return;
        }
        this.pendingStickerPoint = point;
        return;
      }
      this.engine.beginStroke(point, this.currentBrush());
    },

    onCanvasTouchMove(event) {
      if (!this.engine) return;
      const touches = event.touches || [];
      if (touches.length >= 2) {
        this.updatePinch(touches);
        return;
      }
      if (this.suppressDrawingUntilRelease) return;
      const point = this.canvasPoint(event);
      if (!point) return;
      if (this.pendingStickerPoint) {
        this.pendingStickerPoint = point;
        return;
      }
      this.engine.appendPoint(point);
    },

    onCanvasTouchEnd(event) {
      if (this.finishPinchIfReleased(event)) return;
      if (!this.engine) return;
      if (this.pendingStickerPoint) {
        this.engine.placeSticker(this.data.selectedPattern, this.pendingStickerPoint);
        this.pendingStickerPoint = null;
        this.syncActionState();
        this.markDirty();
        return;
      }
      if (this.engine.endStroke()) {
        this.syncActionState();
        this.markDirty();
      }
    },

    onCanvasTouchCancel(event) {
      if (this.finishPinchIfReleased(event)) return;
      this.pendingStickerPoint = null;
      if (!this.engine) return;
      if (this.engine.endStroke()) {
        this.syncActionState();
        this.markDirty();
      }
    }
  }
});
