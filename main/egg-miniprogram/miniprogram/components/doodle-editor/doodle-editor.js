const petStore = require('../../utils/pet-store');
const canvas = require('./doodle-canvas');

const TOOL_BRUSH = 'brush';
const TOOL_ERASER = 'eraser';
const PREVIEW_RPX_RATIO = 2;

// 构造画笔尺寸选项：预览点大小按 px * 2 计算，但不小于 6rpx
function buildBrushOptions(pixelsList) {
  return pixelsList.map(pixels => ({
    pixels,
    previewRpx: Math.max(6, pixels * PREVIEW_RPX_RATIO)
  }));
}

Component({
  properties: {
    visible: { type: Boolean, value: false },
    petId: { type: String, value: '' }
  },

  data: {
    statusBarHeight: 20,
    brushColors: canvas.BRUSH_COLORS,
    brushSizes: buildBrushOptions(canvas.BRUSH_SIZES),
    eraserSizes: buildBrushOptions(canvas.ERASER_SIZES),
    selectedColor: canvas.DEFAULT_BRUSH_COLOR,
    selectedColorName: canvas.BRUSH_COLORS[0].name,
    activeTool: TOOL_BRUSH,
    toolPanelOpen: false,
    colorPickerOpen: false,
    brushSizeIndex: 1,
    eraserSizeIndex: 2,
    toolSizeLabel: `${canvas.BRUSH_SIZES[1]} px`,
    toolSizeValue: 2,
    toolSizeMin: 1,
    toolSizeMax: canvas.BRUSH_SIZES.length,
    canUndo: false,
    canClear: false,
    canvasReady: false,
    canvasScale: 1
  },

  lifetimes: {
    attached() {
      this.engine = null;
      this.pageActive = false;
      this.setupToken = 0;
      const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
      this.setData({ statusBarHeight: info.statusBarHeight || 20 });
    },

    detached() {
      this.disposeEngine();
      this.pageActive = false;
    }
  },

  observers: {
    'visible': function (visible) {
      if (visible) this.enterEditor();
      else this.exitEditor();
    }
  },

  methods: {
    /** 进入编辑器：读取宠物蛋壳底色并初始化 canvas 引擎 */
    enterEditor() {
      if (this.pageActive) return;
      this.pageActive = true;
      const pet = petStore.getPet();
      const shellColor = pet && pet.shell && pet.shell.color ? pet.shell.color : '#EDE78E';
      this.initEngine(shellColor);
      this.setData({ canvasReady: false });
      wx.nextTick(() => {
        if (!this.pageActive) return;
        this.setData({ canvasReady: true });
      });
    },

    /** 退出编辑器：释放 canvas 层 */
    exitEditor() {
      this.pageActive = false;
      this.disposeEngine();
    },

    /** 初始化画笔引擎 */
    initEngine(shellColor) {
      this.disposeEngine();
      this.setupToken += 1;
      const token = this.setupToken;
      this.engine = canvas.createEngine({
        page: this,
        selectors: { base: '#eggBaseCanvas', art: '#eggArtCanvas' },
        shellColor
      });
      this.engine.init().then(() => {
        if (token !== this.setupToken || !this.pageActive) return;
        this.syncActionState();
      });
      this.engine.setBrush({
        color: this.data.selectedColor,
        size: this.getCurrentSize(),
        tool: this.data.activeTool
      });
    },

    /** 释放引擎与 canvas 引用 */
    disposeEngine() {
      this.setupToken += 1;
      if (this.engine) {
        this.engine.dispose();
        this.engine = null;
      }
    },

    /** 同步撤销/清空可用状态 */
    syncActionState() {
      const strokes = this.engine ? this.engine.getStrokes() : { list: [] };
      this.setData({
        canUndo: strokes.list.length > 0,
        canClear: strokes.list.length > 0
      });
    },

    /** 获取当前工具对应的笔刷尺寸像素值 */
    getCurrentSize() {
      const options = this.data.activeTool === TOOL_ERASER ? canvas.ERASER_SIZES : canvas.BRUSH_SIZES;
      const index = this.data.activeTool === TOOL_ERASER ? this.data.eraserSizeIndex : this.data.brushSizeIndex;
      return options[index] || options[1];
    },

    /** 收起工具面板 */
    collapseToolPanel() {
      if (!this.data.toolPanelOpen) return;
      this.setData({ toolPanelOpen: false, colorPickerOpen: false });
    },

    /** 点击画布背景，收起工具面板 */
    onCanvasBackdropTap() {
      this.collapseToolPanel();
    },

    /** 阻止面板内部点击冒泡 */
    onToolPanelTap() {},

    /** 切换画笔/橡皮擦工具 */
    onTool(event) {
      const tool = event.currentTarget.dataset.tool;
      if (tool !== TOOL_BRUSH && tool !== TOOL_ERASER) return;
      const options = tool === TOOL_ERASER ? canvas.ERASER_SIZES : canvas.BRUSH_SIZES;
      const index = tool === TOOL_ERASER ? this.data.eraserSizeIndex : this.data.brushSizeIndex;
      this.setData({
        activeTool: tool,
        toolPanelOpen: true,
        colorPickerOpen: false,
        toolSizeValue: index + 1,
        toolSizeMin: 1,
        toolSizeMax: options.length,
        toolSizeLabel: `${options[index]} px`
      }, () => {
        this.engine.setBrush({ color: this.data.selectedColor, size: this.getCurrentSize(), tool });
      });
    },

    /** 打开/关闭颜色选择器 */
    onToggleBrushColorPicker() {
      if (this.data.activeTool !== TOOL_BRUSH) return;
      this.setData({ colorPickerOpen: !this.data.colorPickerOpen });
    },

    /** 颜色选择器内部点击拦截 */
    onColorPickerTap() {},

    /** 选择画笔颜色 */
    onBrushColor(event) {
      const token = event.currentTarget.dataset.token;
      const color = canvas.BRUSH_COLORS.find(item => item.token === token);
      if (!color) return;
      this.setData({
        selectedColor: color.value,
        selectedColorName: color.name,
        activeTool: TOOL_BRUSH,
        colorPickerOpen: false
      }, () => {
        this.engine.setBrush({ color: color.value, size: this.getCurrentSize(), tool: TOOL_BRUSH });
      });
    },

    /** 选择画笔粗细 */
    onBrushSize(event) {
      this.selectBrushSize(Number(event.currentTarget.dataset.index));
    },

    selectBrushSize(index) {
      const safeIndex = Math.max(0, Math.min(canvas.BRUSH_SIZES.length - 1, Number(index) || 0));
      const pixels = canvas.BRUSH_SIZES[safeIndex];
      this.setData({
        brushSizeIndex: safeIndex,
        toolSizeValue: safeIndex + 1,
        toolSizeLabel: `${pixels} px`,
        colorPickerOpen: false
      }, () => {
        if (this.data.activeTool === TOOL_BRUSH) {
          this.engine.setBrush({ color: this.data.selectedColor, size: pixels, tool: TOOL_BRUSH });
        }
      });
    },

    /** 选择橡皮擦粗细 */
    onEraserSize(event) {
      this.selectEraserSize(Number(event.currentTarget.dataset.index));
    },

    selectEraserSize(index) {
      const safeIndex = Math.max(0, Math.min(canvas.ERASER_SIZES.length - 1, Number(index) || 0));
      const pixels = canvas.ERASER_SIZES[safeIndex];
      this.setData({
        eraserSizeIndex: safeIndex,
        toolSizeValue: safeIndex + 1,
        toolSizeLabel: `${pixels} px`
      }, () => {
        if (this.data.activeTool === TOOL_ERASER) {
          this.engine.setBrush({ color: this.data.selectedColor, size: pixels, tool: TOOL_ERASER });
        }
      });
    },

    /** 撤销上一步 */
    onUndo() {
      this.collapseToolPanel();
      if (!this.engine || !this.data.canUndo) return;
      this.engine.undo();
      this.syncActionState();
    },

    /** 清空画布 */
    onClear() {
      this.collapseToolPanel();
      if (!this.engine || !this.data.canClear) return;
      this.engine.clear();
      this.syncActionState();
    },

    /** 触摸开始：落笔 */
    onCanvasTouchStart(event) {
      this.collapseToolPanel();
      if (!this.engine) return;
      this.engine.touchStart(event, this.data.canvasScale);
    },

    /** 触摸移动：连点成线 */
    onCanvasTouchMove(event) {
      if (!this.engine) return;
      this.engine.touchMove(event, this.data.canvasScale);
    },

    /** 触摸结束：收笔并刷新可撤销状态 */
    onCanvasTouchEnd() {
      if (!this.engine) return;
      this.engine.touchEnd();
      this.syncActionState();
    },

    /** 触摸取消：同结束处理 */
    onCanvasTouchCancel() {
      this.onCanvasTouchEnd();
    },

    /** 保存：导出画布并抛出 saved 事件，由调用方 orchestrate 上传与 hatch-action */
    async onSave() {
      if (!this.engine) return;
      this.collapseToolPanel();
      const tempFilePath = await this.engine.exportArtwork();
      this.triggerEvent('saved', { tempFilePath });
    },

    /** 关闭编辑器 */
    onClose() {
      this.collapseToolPanel();
      this.triggerEvent('close');
    }
  }
});
