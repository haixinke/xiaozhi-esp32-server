import assert from 'node:assert/strict';
import test from 'node:test';

import { gateCheckWeb } from '../src/utils/gateTest.js';

test('gateCheckWeb 返回 "ok"', () => {
  assert.equal(gateCheckWeb(), 'ok');
});
