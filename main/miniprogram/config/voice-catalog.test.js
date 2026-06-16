const assert = require('assert');
const cat = require('./voice-catalog');

assert.strictEqual(Array.isArray(cat.DEFAULT_VOICES), true);
assert.strictEqual(cat.DEFAULT_VOICES.length >= 4, true);
cat.DEFAULT_VOICES.forEach(function (v) {
  assert.ok(v.id, 'voice missing id');
  assert.ok(v.label, 'voice missing label');
  assert.ok(v.audioUrl, 'voice missing audioUrl');
});
const hit = cat.findById(cat.DEFAULT_VOICES[0].id);
assert.strictEqual(hit.label, cat.DEFAULT_VOICES[0].label);
console.log('voice-catalog.test.js OK');
