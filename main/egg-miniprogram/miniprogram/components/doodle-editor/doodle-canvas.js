// 蛋壳涂鸦画布引擎：操作（笔画/贴纸）模型 + 双层 canvas 渲染。
// 与静态 UI 项目的 egg-shell-art 对齐：画笔像素印章、橡皮 destination-out、贴纸像素画。
const canvas2d = require('../../utils/canvas-2d');

const BRUSH_COLORS = [
  { token: 'forest', name: '森林绿', value: '#526B4D' },
  { token: 'apricot-orange', name: '杏子橙', value: '#D98652' },
  { token: 'lake-blue', name: '湖水蓝', value: '#5F8FA8' },
  { token: 'berry-pink', name: '莓果粉', value: '#C97682' },
  { token: 'grape-purple', name: '葡萄紫', value: '#8573A3' },
  { token: 'mist-sage', name: '雾松绿', value: '#AFC29A' },
  { token: 'butter-yellow', name: '奶油黄', value: '#E6CE73' },
  { token: 'sky-blue', name: '晴空蓝', value: '#9EC7D8' },
  { token: 'wine-red', name: '葡萄酒红', value: '#7B3E52' },
  { token: 'lavender', name: '浅藤紫', value: '#B9ABD2' }
];
const DEFAULT_BRUSH_COLOR = BRUSH_COLORS[0].value;

const BRUSH_REFERENCE_PX = 180;
const ERASER_REFERENCE_PX = 150;
const BRUSH_SIZES = [2, 5, 8, 12, 18].map(pixels => ({ label: `${pixels} px`, pixels, width: pixels / BRUSH_REFERENCE_PX }));
const ERASER_SIZES = [6, 10, 15, 22, 30].map(pixels => ({ label: `${pixels} px`, pixels, width: pixels / ERASER_REFERENCE_PX }));
const ERASER_DEFAULT_PX = 15;
const DEFAULT_BRUSH_WIDTH = BRUSH_SIZES[1].width;
const DEFAULT_ERASER_WIDTH = ERASER_DEFAULT_PX / ERASER_REFERENCE_PX;

const PATTERNS = [
  { type: 'star', name: '星星', symbol: '✦' },
  { type: 'heart', name: '爱心', symbol: '♡' },
  { type: 'leaf', name: '叶子', symbol: '⌁' }
];
const PIXEL_STICKERS = {
  star: [
    '0001000',
    '0001000',
    '1101011',
    '0111110',
    '0011100',
    '0110110',
    '1100011'
  ],
  heart: [
    '0110110',
    '1111111',
    '1111111',
    '0111110',
    '0011100',
    '0001000'
  ],
  leaf: [
    '0000110',
    '0001110',
    '0011100',
    '0111000',
    '1110000',
    '0100000'
  ]
};

const MAX_OPERATIONS = 240;
const MAX_POINTS_PER_STROKE = 300;
const MIN_POINT_DISTANCE = 0.006;
const MIN_CANVAS_SCALE = 1;
const MAX_CANVAS_SCALE = 1.6;

function clamp(value, min, max) {
  const n = Number(value);
  return Number.isFinite(n) ? Math.max(min, Math.min(max, n)) : min;
}

// 导出坐标量化到 4 位小数，控制本地缓存(shell JSON)体积；引擎内部仍用原始精度
function quantizeCoord(value) {
  const n = Number(value);
  return Number.isFinite(n) ? Math.round(n * 10000) / 10000 : 0;
}

// 导出用：把一条操作的坐标量化为 4 位小数（不改引擎内部状态，作用于 snapshot 副本）
function quantizeOperation(operation) {
  if (!operation || typeof operation !== 'object') return operation;
  if (operation.type === 'stroke') {
    return Object.assign({}, operation, {
      points: (operation.points || []).map(p => ({ x: quantizeCoord(p.x), y: quantizeCoord(p.y) }))
    });
  }
  if (operation.type === 'sticker') {
    return Object.assign({}, operation, { x: quantizeCoord(operation.x), y: quantizeCoord(operation.y) });
  }
  return operation;
}

function brushWidthForPixels(pixels, canvasSize) {
  const reference = Math.max(1, Number(canvasSize) || BRUSH_REFERENCE_PX);
  const safePixels = BRUSH_SIZES.some(item => item.pixels === Number(pixels)) ? Number(pixels) : BRUSH_SIZES[1].pixels;
  return clamp(safePixels / reference, 0.004, 0.3);
}

function eraserWidthForPixels(pixels, canvasSize) {
  const reference = Math.max(1, Number(canvasSize) || ERASER_REFERENCE_PX);
  return clamp(clamp(pixels, 4, 30) / reference, 0.008, 0.3);
}

function createStroke(tool, points, sequence, width, color) {
  const safeTool = tool === 'eraser' ? 'eraser' : 'brush';
  return {
    id: `stroke-${Math.max(0, Number(sequence) || 0) + 1}`,
    type: 'stroke',
    tool: safeTool,
    points: (points || []).map(p => ({ x: clamp(p.x, 0, 1), y: clamp(p.y, 0, 1) })),
    width: width || (safeTool === 'eraser' ? DEFAULT_ERASER_WIDTH : DEFAULT_BRUSH_WIDTH),
    color: safeTool === 'brush' ? (color || DEFAULT_BRUSH_COLOR) : DEFAULT_BRUSH_COLOR
  };
}

function createSticker(pattern, sequence, point) {
  const safePattern = PATTERNS.some(item => item.type === pattern) ? pattern : PATTERNS[0].type;
  const hasPoint = point && Number.isFinite(Number(point.x)) && Number.isFinite(Number(point.y));
  return {
    id: `sticker-${Math.max(0, Number(sequence) || 0) + 1}`,
    type: 'sticker',
    pattern: safePattern,
    x: hasPoint ? clamp(point.x, 0.12, 0.88) : 0.5,
    y: hasPoint ? clamp(point.y, 0.12, 0.9) : 0.5
  };
}

function eggPath(context, width, height) {
  context.beginPath();
  context.moveTo(width * 0.5, height * 0.035);
  context.bezierCurveTo(width * 0.28, height * 0.035, width * 0.12, height * 0.29, width * 0.1, height * 0.55);
  context.bezierCurveTo(width * 0.075, height * 0.8, width * 0.24, height * 0.965, width * 0.5, height * 0.975);
  context.bezierCurveTo(width * 0.76, height * 0.965, width * 0.925, height * 0.8, width * 0.9, height * 0.55);
  context.bezierCurveTo(width * 0.88, height * 0.29, width * 0.72, height * 0.035, width * 0.5, height * 0.035);
  context.closePath();
}

function drawFallbackEgg(context, width, height, shellColor) {
  const gradient = context.createLinearGradient(0, 0, width, height);
  gradient.addColorStop(0, '#FFFDF6');
  gradient.addColorStop(0.62, shellColor || '#EDE78E');
  gradient.addColorStop(1, '#D9D2C3');
  eggPath(context, width, height);
  context.fillStyle = gradient;
  context.fill();
}

function drawHighlight(context, width, height) {
  context.save();
  context.translate(width * 0.34, height * 0.27);
  context.rotate(0.2);
  const highlight = context.createRadialGradient(0, 0, 0, 0, 0, width * 0.15);
  highlight.addColorStop(0, 'rgba(255,255,255,.72)');
  highlight.addColorStop(1, 'rgba(255,255,255,0)');
  context.fillStyle = highlight;
  context.beginPath();
  context.ellipse(0, 0, width * 0.12, height * 0.11, 0, 0, Math.PI * 2);
  context.fill();
  context.restore();
}

function drawBase(context, image, width, height, shellColor) {
  context.clearRect(0, 0, width, height);
  if (!image) {
    drawFallbackEgg(context, width, height, shellColor);
    drawHighlight(context, width, height);
    return;
  }
  context.drawImage(image, 0, 0, width, height);
}

function drawSticker(context, operation, width, height) {
  const pixels = PIXEL_STICKERS[operation.pattern] || PIXEL_STICKERS.star;
  const cellSize = Math.max(2, Math.round(Math.min(width, height) * 0.012));
  const columns = Math.max(...pixels.map(row => row.length));
  const rows = pixels.length;
  const startX = Math.round(operation.x * width - columns * cellSize / 2);
  const startY = Math.round(operation.y * height - rows * cellSize / 2);
  context.save();
  context.fillStyle = 'rgba(70,91,62,.58)';
  pixels.forEach((row, rowIndex) => {
    Array.from(row).forEach((pixel, columnIndex) => {
      if (pixel !== '1') return;
      context.fillRect(startX + columnIndex * cellSize, startY + rowIndex * cellSize, cellSize, cellSize);
    });
  });
  context.restore();
}

function drawPixelStroke(context, operation, width, height) {
  const points = operation.points || [];
  if (!points.length) return;
  const pixelSize = Math.max(1, Math.round(Math.min(width, height) * operation.width));
  context.save();
  context.globalCompositeOperation = 'source-over';
  context.fillStyle = operation.color || '#536447';
  const stamp = (point) => {
    const x = Math.round(point.x * width - pixelSize / 2);
    const y = Math.round(point.y * height - pixelSize / 2);
    context.fillRect(x, y, pixelSize, pixelSize);
  };
  if (points.length === 1) {
    stamp(points[0]);
  } else {
    points.slice(1).forEach((point, index) => {
      const previous = points[index];
      const startX = previous.x * width;
      const startY = previous.y * height;
      const endX = point.x * width;
      const endY = point.y * height;
      const distance = Math.hypot(endX - startX, endY - startY);
      const steps = Math.max(1, Math.ceil(distance / Math.max(1, pixelSize * 0.55)));
      for (let step = 0; step <= steps; step += 1) {
        const ratio = step / steps;
        stamp({ x: (startX + (endX - startX) * ratio) / width, y: (startY + (endY - startY) * ratio) / height });
      }
    });
  }
  context.restore();
}

function drawEraserStroke(context, operation, width, height) {
  const points = operation.points || [];
  if (!points.length) return;
  context.save();
  context.globalCompositeOperation = 'destination-out';
  context.lineWidth = Math.min(width, height) * operation.width;
  context.lineCap = 'round';
  context.lineJoin = 'round';
  if (points.length === 1) {
    context.beginPath();
    context.arc(points[0].x * width, points[0].y * height, context.lineWidth / 2, 0, Math.PI * 2);
    context.fill();
  } else {
    context.beginPath();
    context.moveTo(points[0].x * width, points[0].y * height);
    points.slice(1).forEach(point => context.lineTo(point.x * width, point.y * height));
    context.stroke();
  }
  context.restore();
}

function drawArt(context, maskImage, width, height, operations, activeOperation) {
  context.clearRect(0, 0, width, height);
  if (!maskImage) {
    context.save();
    eggPath(context, width, height);
    context.clip();
  }
  (operations || []).concat(activeOperation || []).forEach(operation => {
    if (!operation) return;
    if (operation.type === 'sticker') drawSticker(context, operation, width, height);
    if (operation.type === 'stroke' && operation.tool === 'eraser') drawEraserStroke(context, operation, width, height);
    if (operation.type === 'stroke' && operation.tool === 'brush') drawPixelStroke(context, operation, width, height);
  });
  if (!maskImage) {
    context.restore();
    return;
  }
  context.save();
  context.globalCompositeOperation = 'destination-in';
  context.drawImage(maskImage, 0, 0, width, height);
  context.restore();
}

function createEngine(options) {
  const page = options && options.page;
  const selectors = (options && options.selectors) || {};
  const baseImageSource = options && options.baseImage;
  const shellColor = options && options.shellColor;

  let baseLayer = null;
  let artLayer = null;
  let baseImage = null;
  let artMaskImage = null;
  let setupToken = 0;
  // 操作列表 + 撤销栈（存操作快照）+ 进行中的笔画
  let operations = [];
  let undoStack = [];
  let currentStroke = null;
  let operationSequence = 0;

  function snapshot() {
    return JSON.parse(JSON.stringify(operations));
  }

  function pushHistory() {
    undoStack = undoStack.concat([snapshot()]).slice(-120);
  }

  function render(activeOperation) {
    if (baseLayer) drawBase(baseLayer.context, baseImage, baseLayer.width, baseLayer.height, shellColor);
    if (artLayer) drawArt(artLayer.context, artMaskImage, artLayer.width, artLayer.height, operations, activeOperation === undefined ? currentStroke : activeOperation);
  }

  function init() {
    setupToken += 1;
    const token = setupToken;
    return Promise.all([
      canvas2d.createLayer(page, selectors.base || '#eggBaseCanvas'),
      canvas2d.createLayer(page, selectors.art || '#eggArtCanvas')
    ]).then(layers => {
      if (token !== setupToken) return;
      baseLayer = layers[0];
      artLayer = layers[1];
      if (!baseLayer || !artLayer) return;
      return Promise.all([
        baseImageSource ? canvas2d.loadImage(baseLayer, baseImageSource) : Promise.resolve(null),
        baseImageSource ? canvas2d.loadImage(artLayer, baseImageSource) : Promise.resolve(null)
      ]).then(images => {
        if (token !== setupToken) return;
        baseImage = images[0];
        artMaskImage = images[1];
        render();
      });
    });
  }

  function dispose() {
    setupToken += 1;
    baseLayer = null;
    artLayer = null;
    baseImage = null;
    artMaskImage = null;
    currentStroke = null;
  }

  function beginStroke(point, brush) {
    if (!point) return;
    pushHistory();
    const tool = brush && brush.tool === 'eraser' ? 'eraser' : 'brush';
    const canvasSize = artLayer ? Math.min(artLayer.width, artLayer.height) : undefined;
    const width = tool === 'eraser'
      ? eraserWidthForPixels(brush && brush.size, canvasSize)
      : brushWidthForPixels(brush && brush.size, canvasSize);
    operationSequence += 1;
    currentStroke = createStroke(tool, [point], operationSequence, width, brush && brush.color);
    render();
  }

  function appendPoint(point) {
    if (!currentStroke || !point) return;
    const points = currentStroke.points;
    const previous = points[points.length - 1];
    if (Math.abs(previous.x - point.x) + Math.abs(previous.y - point.y) < MIN_POINT_DISTANCE) return;
    currentStroke.points = points.concat(point).slice(-MAX_POINTS_PER_STROKE);
    render();
  }

  function endStroke() {
    if (!currentStroke) return false;
    operations = operations.concat(currentStroke).slice(-MAX_OPERATIONS);
    currentStroke = null;
    render(null);
    return true;
  }

  function cancelStroke() {
    if (!currentStroke) return;
    currentStroke = null;
    if (undoStack.length) undoStack.pop();
    render(null);
  }

  function placeSticker(pattern, point) {
    pushHistory();
    operationSequence += 1;
    operations = operations.concat(createSticker(pattern, operationSequence, point)).slice(-MAX_OPERATIONS);
    render(null);
  }

  function undo() {
    const snapshotOps = undoStack.pop();
    if (!snapshotOps) return;
    operations = snapshotOps;
    currentStroke = null;
    render(null);
  }

  function clear() {
    if (!operations.length) return;
    pushHistory();
    operations = [];
    currentStroke = null;
    render(null);
  }

  // 导出操作序列(shell)供本地持久化：深拷贝 + 坐标量化，外部拿到独立副本，改它不影响引擎
  function getOperations() {
    return snapshot().map(quantizeOperation);
  }

  // 从持久化的 shell 恢复操作序列：逐条归一化(非法项丢弃)，重置序号防 id 冲突，清空撤销栈后重绘
  function restoreOperations(list) {
    const source = Array.isArray(list) ? list : [];
    const restored = [];
    source.forEach(item => {
      if (!item || typeof item !== 'object') return;
      if (item.type === 'stroke') {
        restored.push(createStroke(item.tool, item.points, restored.length, item.width, item.color));
      } else if (item.type === 'sticker') {
        restored.push(createSticker(item.pattern, restored.length, { x: item.x, y: item.y }));
      }
    });
    operations = restored.slice(-MAX_OPERATIONS);
    operationSequence = operations.length;
    currentStroke = null;
    undoStack = [];
    render(null);
  }

  // 导出成品图：艺术层叠到底图之上合成整颗蛋
  function exportArtwork() {
    if (!artLayer) return Promise.resolve('');
    render(null);
    if (baseLayer) {
      baseLayer.context.save();
      baseLayer.context.drawImage(artLayer.canvas, 0, 0, baseLayer.width, baseLayer.height);
      baseLayer.context.restore();
    }
    return canvas2d.exportImage(baseLayer).then(tempFilePath => {
      // 导出后重绘底层，擦掉合成上去的艺术层，保持画布干净
      render(null);
      return tempFilePath;
    });
  }

  return {
    init,
    dispose,
    render,
    beginStroke,
    appendPoint,
    endStroke,
    cancelStroke,
    placeSticker,
    undo,
    clear,
    getOperations,
    restoreOperations,
    exportArtwork,
    canvasSize: () => (artLayer ? Math.min(artLayer.width, artLayer.height) : 0),
    layerRect: () => (artLayer ? { left: artLayer.left || 0, top: artLayer.top || 0 } : { left: 0, top: 0 }),
    canUndo: () => undoStack.length > 0,
    canClear: () => operations.length > 0 || !!currentStroke,
    // 测试口：注入假层，跳过 wx.createSelectorQuery
    _setLayersForTest(base, art) {
      baseLayer = base;
      artLayer = art;
      baseImage = null;
      artMaskImage = null;
    }
  };
}

module.exports = {
  BRUSH_COLORS,
  BRUSH_SIZES,
  ERASER_SIZES,
  ERASER_DEFAULT_PX,
  PATTERNS,
  DEFAULT_BRUSH_COLOR,
  MIN_CANVAS_SCALE,
  MAX_CANVAS_SCALE,
  createStroke,
  createSticker,
  createEngine
};
