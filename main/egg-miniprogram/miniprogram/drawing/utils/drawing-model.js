// 纸张画板及完成门槛全部驻留本地，画作、坐标与作画时间不发送至后端。
const MAX_POINTS = 12000;
const MAX_STROKES = 400;

function createDrawingModel() {
  let operations = [];
  let history = [];
  let current = null;
  function pointCount() {
    return operations.reduce((sum, item) => sum + item.points.length, 0)
      + (current ? current.points.length : 0);
  }
  function begin(tool, color, width, point, sticker) {
    if (operations.length >= MAX_STROKES || pointCount() >= MAX_POINTS) return false;
    current = { tool, color, width, sticker, points: [{ ...point }] };
    return true;
  }
  function move(point) {
    if (!current || pointCount() >= MAX_POINTS) return false;
    current = { ...current, points: current.points.concat({ ...point }) };
    return true;
  }
  function end(cancelled) {
    if (current && !cancelled) {
      history = history.concat([operations]);
      operations = operations.concat(current);
    }
    current = null;
  }
  function evaluate() {
    const strokes = operations.filter(item => item.tool === 'brush');
    let activeMs = 0;
    const cells = new Set();
    strokes.forEach(stroke => stroke.points.forEach((point, index) => {
      if (index) {
        const gap = point.t - stroke.points[index - 1].t;
        if (gap > 0 && gap <= 1200) activeMs += gap;
      }
      const col = Math.min(9, Math.max(0, Math.floor(point.x * 10)));
      const row = Math.min(9, Math.max(0, Math.floor(point.y * 10)));
      cells.add(row * 10 + col);
    }));
    return { ok: strokes.length >= 5 && activeMs >= 5000 && cells.size >= 5,
      strokes: strokes.length, activeMs, cells: cells.size };
  }
  return {
    begin, move, end, evaluate,
    undo() {
      current = null;
      if (!history.length) return;
      operations = history[history.length - 1];
      history = history.slice(0, -1);
    },
    clear() {
      current = null;
      if (operations.length) history = history.concat([operations]);
      operations = [];
    },
    canUndo() { return history.length > 0; },
    hasContent() { return operations.length > 0 || Boolean(current); },
    operations() { return current ? operations.concat(current) : operations.slice(); }
  };
}

module.exports = { createDrawingModel, MAX_POINTS, MAX_STROKES };
