const assert = require('assert');
const { openDrawing } = require('./drawing-entry');

// 从两个真实入口共享的公开导航边界验证资格，避免把未破壳宠物带入收费开局。
const calls = [];
global.wx = {
  showToast: ({ title }) => calls.push(['toast', title]),
  navigateTo: (options) => calls.push(['navigate', options])
};
for (const pet of [null, {}, { id: 'egg', hatchStatus: 'EGG' }]) {
  let closed = false;
  assert.strictEqual(openDrawing(pet, 'letter', () => { closed = true; }), false);
  assert.strictEqual(closed, false, '拒绝进入时应保留小纸条弹层');
  assert.strictEqual(calls.at(-1)[0], 'toast');
}
let closed = false;
assert.strictEqual(openDrawing({ id: 'hatched', hatchStatus: 'HATCHED' }, 'letter', () => {
  closed = true;
}), true);
assert.strictEqual(closed, true);
assert.strictEqual(calls.at(-1)[1].url, '/drawing/pages/draw/draw?source=letter');
calls.at(-1)[1].fail();
assert.deepStrictEqual(calls.at(-1), ['toast', '暂时打不开，请稍后再试']);
openDrawing({ id: 'hatched', hatchStatus: 'HATCHED' }, 'activity');
assert.strictEqual(calls.at(-1)[1].url, '/drawing/pages/draw/draw?source=activity');
console.log('drawing-entry.test.js: ALL PASS');
