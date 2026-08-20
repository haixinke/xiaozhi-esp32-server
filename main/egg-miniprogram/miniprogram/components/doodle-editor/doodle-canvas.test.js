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

// ===== 序列化与恢复（shell 本地缓存）=====
const eng2 = engine.createEngine({ page: null, shellColor: '#EDE78E' });
eng2._setLayersForTest(fakeLayer(), fakeLayer());
eng2.beginStroke({ x: 0.123456789, y: 0.5 }, { tool: 'brush', color: '#526B4D', size: 5 });
eng2.appendPoint({ x: 0.5, y: 0.987654321 });
eng2.endStroke();
eng2.placeSticker('heart', { x: 0.333333333, y: 0.4 });

// getOperations 返回深拷贝：改返回值不影响引擎内部
const exported = eng2.getOperations();
assert.strictEqual(exported.length, 2, '导出 1 笔 + 1 贴纸');
assert.strictEqual(exported[0].type, 'stroke');
assert.strictEqual(exported[1].type, 'sticker');
// 导出侧坐标已量化到 4 位小数
assert.strictEqual(exported[0].points[0].x, 0.1235, '笔画点 x 量化为 4 位小数');
assert.strictEqual(exported[0].points[1].y, 0.9877, '笔画点 y 量化为 4 位小数');
assert.strictEqual(exported[1].x, 0.3333, '贴纸 x 量化为 4 位小数');
exported[0].points[0].x = 0.999;
const reExported = eng2.getOperations();
assert.strictEqual(reExported[0].points[0].x, 0.1235, '外部篡改导出副本不影响引擎');

// restoreOperations 重建 + 重置序号 + 清空撤销栈（用未被篡改的干净导出副本）
const cleanExport = eng2.getOperations();
const eng3 = engine.createEngine({ page: null, shellColor: '#EDE78E' });
eng3._setLayersForTest(fakeLayer(), fakeLayer());
eng3.restoreOperations(cleanExport);
assert.strictEqual(eng3.canClear(), true, '恢复后有操作');
assert.strictEqual(eng3.canUndo(), false, '恢复后撤销栈为空（以当前为基线）');
// 恢复会按新引擎的序号重排 id，故只比对内容字段（点/贴纸坐标、颜色、类型、pattern）
const restored = eng3.getOperations();
assert.strictEqual(restored.length, cleanExport.length, '往返操作数一致');
assert.deepStrictEqual(
  restored.map(({ id, ...rest }) => rest),
  cleanExport.map(({ id, ...rest }) => rest),
  '往返内容(除重排id外)一致'
);
// 恢复后继续编辑不 id 冲突
eng3.beginStroke({ x: 0.1, y: 0.1 }, { tool: 'brush', color: '#526B4D', size: 5 });
eng3.endStroke();
const afterNew = eng3.getOperations();
assert.strictEqual(afterNew.length, 3, '恢复后可继续追加');
const ids = afterNew.map(o => o.id);
assert.strictEqual(new Set(ids).size, ids.length, '恢复后新操作 id 不冲突');

// 非法/未知项被丢弃不报错
const eng4 = engine.createEngine({ page: null, shellColor: '#EDE78E' });
eng4._setLayersForTest(fakeLayer(), fakeLayer());
eng4.restoreOperations([
  { type: 'stroke', tool: 'brush', points: [{ x: 0.2, y: 0.2 }], width: 0.03, color: '#526B4D' },
  { type: 'unknown-junk', foo: 1 },
  null,
  { type: 'sticker', pattern: 'rocket', x: 0.5, y: 0.5 },
  'garbage'
]);
const cleaned = eng4.getOperations();
assert.strictEqual(cleaned.length, 2, '非法项被丢弃，保留合法笔画+贴纸');
assert.strictEqual(cleaned[1].pattern, 'star', '未知贴纸类型回退星星');
// 恢复非数组/空也不报错
const eng5 = engine.createEngine({ page: null, shellColor: '#EDE78E' });
eng5._setLayersForTest(fakeLayer(), fakeLayer());
eng5.restoreOperations(null);
eng5.restoreOperations('not-an-array');
assert.strictEqual(eng5.canClear(), false, '非法输入恢复为空画布');

console.log('doodle-canvas.test.js: ALL PASS');
