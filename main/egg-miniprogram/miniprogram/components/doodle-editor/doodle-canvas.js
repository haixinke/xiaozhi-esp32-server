const canvas2d = require('../../utils/canvas-2d');

// 从 STATIC egg-shell-art.js 内联的 10 色画笔色板
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

const BRUSH_SIZES = [2, 5, 8, 12, 18];
const ERASER_SIZES = [6, 10, 15, 22, 30];
const BRUSH_REFERENCE_PX = 180;
const ERASER_REFERENCE_PX = 150;
const MIN_POINT_DISTANCE = 0.006;
const MAX_POINTS_PER_STROKE = 300;

function clamp(value, min, max) {
  const n = Number(value);
  return Number.isFinite(n) ? Math.max(min, Math.min(max, n)) : min;
}

function hexToRgba(hex, alpha) {
  const value = String(hex || '').replace('#', '');
  const normalized = value.length === 3 ? value.split('').map(c => c + c).join('') : value;
  const parsed = /^[0-9a-f]{6}$/i.test(normalized) ? parseInt(normalized, 16) : 0xEDE78E;
  return `rgba(${(parsed >> 16) & 255},${(parsed >> 8) & 255},${parsed & 255},${clamp(alpha, 0, 1)})`;
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

function drawPixelStroke(context, stroke, width, height) {
  const points = stroke.points || [];
  if (!points.length) return;
  const pixelSize = Math.max(1, Math.round(Math.min(width, height) * (stroke.width || 0.028)));
  context.save();
  context.globalCompositeOperation = 'source-over';
  context.fillStyle = stroke.color || '#526B4D';
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

function drawEraserStroke(context, stroke, width, height) {
  const points = stroke.points || [];
  if (!points.length) return;
  context.save();
  context.globalCompositeOperation = 'destination-out';
  context.lineWidth = Math.min(width, height) * (stroke.width || 0.1);
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

function drawArt(context, maskImage, width, height, strokes, currentStroke) {
  context.clearRect(0, 0, width, height);
  if (!maskImage) {
    context.save();
    eggPath(context, width, height);
    context.clip();
  }
  strokes.list.forEach(stroke => {
    if (stroke.tool === 'eraser') drawEraserStroke(context, stroke, width, height);
    else drawPixelStroke(context, stroke, width, height);
  });
  if (currentStroke) {
    if (currentStroke.tool === 'eraser') drawEraserStroke(context, currentStroke, width, height);
    else drawPixelStroke(context, currentStroke, width, height);
  }
  if (!maskImage) {
    context.restore();
    return;
  }
  context.save();
  context.globalCompositeOperation = 'destination-in';
  context.drawImage(maskImage, 0, 0, width, height);
  context.restore();
}

function createStrokeStore() {
  return { list: [], history: [], current: null };
}

function beginStroke(strokes, point) {
  if (!point || !Number.isFinite(Number(point.x)) || !Number.isFinite(Number(point.y))) return;
  strokes.history.push(strokes.list.slice());
  const size = clamp(point.size, 1, 100);
  strokes.current = {
    tool: point.tool === 'eraser' ? 'eraser' : 'brush',
    color: point.color || DEFAULT_BRUSH_COLOR,
    width: point.tool === 'eraser' ? size / ERASER_REFERENCE_PX : size / BRUSH_REFERENCE_PX,
    points: [{ x: clamp(point.x, 0, 1), y: clamp(point.y, 0, 1) }]
  };
}

function appendPoint(strokes, point) {
  if (!strokes.current || !point || !Number.isFinite(Number(point.x)) || !Number.isFinite(Number(point.y))) return;
  const x = clamp(point.x, 0, 1);
  const y = clamp(point.y, 0, 1);
  const points = strokes.current.points;
  const previous = points[points.length - 1];
  const dx = Math.abs(previous.x - x);
  const dy = Math.abs(previous.y - y);
  if (dx + dy < MIN_POINT_DISTANCE) return;
  strokes.current.points = points.concat({ x, y });
  if (strokes.current.points.length > MAX_POINTS_PER_STROKE) {
    strokes.current.points = strokes.current.points.slice(-MAX_POINTS_PER_STROKE);
  }
}

function endStroke(strokes) {
  if (!strokes.current) return;
  strokes.list.push(strokes.current);
  strokes.current = null;
}

function undo(strokes) {
  const snapshot = strokes.history.pop();
  if (!snapshot) return;
  strokes.list = snapshot;
  strokes.current = null;
}

function clear(strokes) {
  strokes.history.push(strokes.list.slice());
  strokes.list = [];
  strokes.current = null;
}

function normalizeBrushSize(size, isEraser) {
  const options = isEraser ? ERASER_SIZES : BRUSH_SIZES;
  const target = clamp(Number(size) || options[1], options[0], options[options.length - 1]);
  return options.reduce((prev, curr) => Math.abs(curr - target) < Math.abs(prev - target) ? curr : prev);
}

function createEngine(options) {
  const page = options && options.page;
  const selectors = options && options.selectors || {};
  const baseImageSource = options && options.baseImage;
  const shellColor = options && options.shellColor;

  let baseLayer = null;
  let artLayer = null;
  let baseImage = null;
  let artMaskImage = null;
  let setupToken = 0;
  const strokes = createStrokeStore();
  let brush = { color: DEFAULT_BRUSH_COLOR, size: BRUSH_SIZES[1], tool: 'brush' };

  function render() {
    if (baseLayer) drawBase(baseLayer.context, baseImage, baseLayer.width, baseLayer.height, shellColor);
    if (artLayer) drawArt(artLayer.context, artMaskImage, artLayer.width, artLayer.height, strokes, strokes.current);
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
  }

  function setBrush(next) {
    const update = next || {};
    if (update.color) brush.color = update.color;
    if (Number.isFinite(Number(update.size))) brush.size = normalizeBrushSize(update.size, update.tool === 'eraser');
    if (update.tool === 'brush' || update.tool === 'eraser') brush.tool = update.tool;
  }

  function canvasPoint(event, scale) {
    const touch = (event.touches || event.changedTouches || [])[0];
    if (!touch || !artLayer) return null;
    const s = Math.max(1, Number(scale) || 1);
    const clientX = Number.isFinite(Number(touch.clientX)) ? Number(touch.clientX) : Number(touch.x);
    const clientY = Number.isFinite(Number(touch.clientY)) ? Number(touch.clientY) : Number(touch.y);
    if (!Number.isFinite(clientX) || !Number.isFinite(clientY)) return null;
    const scaledWidth = artLayer.width * s;
    const scaledHeight = artLayer.height * s;
    const scaledLeft = artLayer.left - (scaledWidth - artLayer.width) / 2;
    const scaledTop = artLayer.top - (scaledHeight - artLayer.height) / 2;
    const x = (clientX - scaledLeft) / scaledWidth;
    const y = (clientY - scaledTop) / scaledHeight;
    return { x: clamp(x, 0, 1), y: clamp(y, 0, 1) };
  }

  function touchStart(event, scale) {
    const point = canvasPoint(event, scale);
    if (!point) return;
    beginStroke(strokes, { x: point.x, y: point.y, color: brush.color, size: brush.size, tool: brush.tool });
    render();
  }

  function touchMove(event, scale) {
    const point = canvasPoint(event, scale);
    if (!point) return;
    appendPoint(strokes, point);
    render();
  }

  function touchEnd() {
    endStroke(strokes);
    render();
  }

  function undoStroke() {
    undo(strokes);
    render();
  }

  function clearStrokes() {
    clear(strokes);
    render();
  }

  function exportArtwork() {
    if (!artLayer) return Promise.resolve('');
    render();
    return canvas2d.exportImage(artLayer);
  }

  return {
    init,
    dispose,
    render,
    setBrush,
    touchStart,
    touchMove,
    touchEnd,
    undo: undoStroke,
    clear: clearStrokes,
    exportArtwork,
    getStrokes: () => strokes,
    getLayerInfo: () => ({ baseLayer, artLayer })
  };
}

module.exports = {
  BRUSH_COLORS,
  BRUSH_SIZES,
  ERASER_SIZES,
  DEFAULT_BRUSH_COLOR,
  createStrokeStore,
  beginStroke,
  appendPoint,
  endStroke,
  undo,
  clear,
  createEngine
};
