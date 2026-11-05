// 通过画板模型验证有效作画、撤销与清空的用户可见规则。
const assert = require('node:assert/strict');
const { createDrawingModel } = require('./drawing-model');

const model = createDrawingModel();
for (let row = 0; row < 5; row += 1) {
  model.begin('brush', '#35483c', 5, { x: 0.1, y: row / 10, t: row * 1200 });
  model.move({ x: 0.8, y: row / 10, t: row * 1200 + 1000 });
  model.end();
}
assert.equal(model.evaluate().ok, true);
assert.equal(model.evaluate().strokes, 5);
model.undo();
assert.equal(model.evaluate().ok, false);
model.begin('sticker', '#35483c', 5, { x: 0.3, y: 0.4, t: 7000 }, 'star');
model.end();
assert.equal(model.evaluate().strokes, 4, '贴纸不能补足笔数');
model.begin('eraser', '#35483c', 5, { x: 0.3, y: 0.4, t: 8000 });
model.move({ x: 0.4, y: 0.5, t: 9000 });
model.end();
assert.equal(model.evaluate().ok, false, '橡皮不能补足笔数或时间');
model.clear();
assert.equal(model.hasContent(), false);
assert.equal(model.evaluate().strokes, 0);
assert.equal(model.canUndo(), true, '清空后仍可撤销');
model.undo();
assert.equal(model.hasContent(), true, '撤销清空可恢复绘画');
assert.equal(model.evaluate().strokes, 4);

const idle = createDrawingModel();
for (let row = 0; row < 5; row += 1) {
  idle.begin('brush', '#35483c', 5, { x: 0.1, y: row / 10, t: row * 4000 });
  idle.move({ x: 0.8, y: row / 10, t: row * 4000 + 3000 });
  idle.end();
}
assert.equal(idle.evaluate().ok, false, '静置或切后台的长间隔不能累计有效作画');
console.log('drawing-model tests passed');
