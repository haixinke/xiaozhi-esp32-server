// Canvas 2D 渲染独立于游戏会话，撤销与橡皮按同一操作序列重绘。
const { drawSticker, drawBrush } = require('./drawing-primitives');

function createCanvasRenderer(page) {
  let layer = null;
  let frame = null;
  let latest = [];
  function renderNow() {
    frame = null;
    if (!layer) return;
    const { ctx, width, height } = layer;
    ctx.clearRect(0, 0, width, height);
    latest.forEach(operation => {
      const points = operation.points;
      if (!points.length) return;
      ctx.save();
      ctx.globalCompositeOperation = operation.tool === 'eraser' ? 'destination-out' : 'source-over';
      ctx.strokeStyle = operation.color;
      ctx.fillStyle = operation.color;
      ctx.lineWidth = operation.width;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      if (operation.tool === 'sticker') {
        drawSticker(ctx, operation, width, height);
      } else if (operation.tool === 'brush') {
        drawBrush(ctx, operation, width, height);
      } else if (points.length === 1) {
        ctx.beginPath();
        ctx.arc(points[0].x * width, points[0].y * height, operation.width / 2, 0, Math.PI * 2);
        ctx.fill();
      } else {
        ctx.beginPath();
        ctx.moveTo(points[0].x * width, points[0].y * height);
        points.slice(1).forEach(point => ctx.lineTo(point.x * width, point.y * height));
        ctx.stroke();
      }
      ctx.restore();
    });
  }
  return {
    init() {
      return new Promise((resolve, reject) => {
        wx.createSelectorQuery().in(page).select('#drawingCanvas').fields({ node: true, size: true, rect: true })
          .exec(result => {
            const rect = result && result[0];
            if (!rect || !rect.node || !rect.width || !rect.height) { reject(new Error('画纸没有准备好')); return; }
            const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
            const ratio = Math.min(3, info.pixelRatio || 2);
            const canvas = rect.node;
            canvas.width = Math.round(rect.width * ratio);
            canvas.height = Math.round(rect.height * ratio);
            const ctx = canvas.getContext('2d');
            ctx.scale(ratio, ratio);
            layer = { ...rect, canvas, ctx };
            resolve();
          });
      });
    },
    point(touch, scale, timestamp) {
      if (!layer || !touch) return null;
      const left = layer.left - layer.width * (scale - 1) / 2;
      const top = layer.top - layer.height * (scale - 1) / 2;
      const localX = Number.isFinite(touch.clientX) ? (touch.clientX - left) / scale : Number(touch.x) / scale;
      const localY = Number.isFinite(touch.clientY) ? (touch.clientY - top) / scale : Number(touch.y) / scale;
      if (!Number.isFinite(localX) || !Number.isFinite(localY)) return null;
      return { x: Math.max(0, Math.min(1, localX / layer.width)),
        y: Math.max(0, Math.min(1, localY / layer.height)), t: timestamp };
    },
    render(operations) {
      latest = operations;
      if (!layer || frame != null) return;
      frame = layer.canvas.requestAnimationFrame(renderNow);
    },
    dispose() {
      if (layer && frame != null) layer.canvas.cancelAnimationFrame(frame);
      frame = null; layer = null;
    }
  };
}

module.exports = { createCanvasRenderer };
