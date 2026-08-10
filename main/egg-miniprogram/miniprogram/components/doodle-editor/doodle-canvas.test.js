const assert = require('assert');
const engine = require('./doodle-canvas');

// ===== 纯函数：笔画/贴纸创建 =====
const stroke = engine.createStroke('brush', [{ x: 0.1, y: 0.2 }, { x: 0.3, y: 0.4 }], 0, 5 / 180, '#526B4D');
assert.strictEqual(stroke.type, 'stroke');
assert.strictEqual(stroke.tool, 'brush');
assert.strictEqual(stroke.points.length, 2);
assert.strictEqual(stroke.color, '#526B4D');
assert.strictEqual(stroke.id, 'stroke-1');

const eraserStroke = engine.createStroke('eraser', [{ x: 0.5, y: 0.5 }], 3);
assert.strictEqual(eraserStroke.tool, 'eraser');
assert.strictEqual(eraserStroke.id, 'stroke-4');

const sticker = engine.createSticker('heart', 7, { x: 0.5, y: 0.5 });
assert.strictEqual(sticker.type, 'sticker');
assert.strictEqual(sticker.pattern, 'heart');
assert.strictEqual(sticker.id, 'sticker-8');
assert.strictEqual(sticker.x, 0.5);

const clampedSticker = engine.createSticker('star', 0, { x: 0.02, y: 0.99 });
assert.strictEqual(clampedSticker.x, 0.12, '贴纸 x 被夹在安全区内');
assert.strictEqual(clampedSticker.y, 0.9, '贴纸 y 被夹在安全区内');

const unknownPattern = engine.createSticker('rocket', 0, { x: 0.5, y: 0.5 });
assert.strictEqual(unknownPattern.pattern, 'star', '未知贴纸类型回退到星星');

// ===== 引擎：笔画生命周期 + 撤销/清空 =====
// 注入假层，绕过 wx.createSelectorQuery
function fakeLayer() {
  const calls = [];
  const gradient = { addColorStop: () => {} };
  const context = new Proxy({}, {
    get(target, prop) {
      if (prop === 'createLinearGradient' || prop === 'createRadialGradient') return () => gradient;
      if (prop in target) return target[prop];
      return (...args) => { calls.push([prop, args.length]); };
    },
    set(target, prop, value) { target[prop] = value; return true; }
  });
  return { canvas: { width: 300, height: 300 }, context, width: 300, height: 300, left: 0, top: 0, calls };
}

const eng = engine.createEngine({ page: null, shellColor: '#EDE78E' });
const base = fakeLayer();
const art = fakeLayer();
eng._setLayersForTest(base, art);

assert.strictEqual(eng.canUndo(), false);
assert.strictEqual(eng.canClear(), false);

eng.beginStroke({ x: 0.2, y: 0.2 }, { tool: 'brush', color: '#526B4D', size: 5 });
eng.appendPoint({ x: 0.25, y: 0.25 });
eng.appendPoint({ x: 0.251, y: 0.251 }); // 距离太近应被忽略
const ended = eng.endStroke();
assert.strictEqual(ended, true, '收笔返回 true');
assert.strictEqual(eng.canUndo(), true, '画完一笔后可撤销');
assert.strictEqual(eng.canClear(), true, '画完一笔后可清空');

eng.placeSticker('leaf', { x: 0.6, y: 0.6 });
assert.strictEqual(eng.canClear(), true);

eng.undo(); // 撤销贴纸
eng.undo(); // 撤销笔画
assert.strictEqual(eng.canClear(), false, '两次撤销后操作列表为空');
assert.strictEqual(eng.canUndo(), false, '撤销栈也空了');

eng.beginStroke({ x: 0.3, y: 0.3 }, { tool: 'brush', color: '#526B4D', size: 5 });
eng.endStroke();
eng.clear();
assert.strictEqual(eng.canClear(), false, '清空后无操作');
assert.strictEqual(eng.canUndo(), true, '清空本身可撤销');
eng.undo();
assert.strictEqual(eng.canClear(), true, '撤销清空后操作恢复');

// 进行中的笔画可被取消（双指缩放时调用）
eng.beginStroke({ x: 0.4, y: 0.4 }, { tool: 'brush', color: '#526B4D', size: 5 });
eng.cancelStroke();
assert.strictEqual(eng.canClear(), true, '取消进行中的笔画后已有操作仍在');

console.log('doodle-canvas.test.js: ALL PASS');
