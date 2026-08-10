const assert = require('assert');
const engine = require('./doodle-canvas');

const strokes = engine.createStrokeStore();
engine.beginStroke(strokes, { x: 0.1, y: 0.2, color: '#526B4D', size: 3 });
engine.appendPoint(strokes, { x: 0.3, y: 0.4 });
engine.endStroke(strokes);
assert.strictEqual(strokes.list.length, 1);
assert.strictEqual(strokes.list[0].points.length, 2);
engine.undo(strokes);
assert.strictEqual(strokes.list.length, 0);
engine.clear(strokes);
assert.strictEqual(strokes.list.length, 0);
console.log('doodle-canvas.test.js: ALL PASS');
