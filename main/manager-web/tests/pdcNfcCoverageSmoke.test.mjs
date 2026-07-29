import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

test('package exposes strict NFC coverage command', async () => {
  const pkg = JSON.parse(await readFile(new URL('../package.json', import.meta.url)));
  assert.match(pkg.scripts['verify:nfc-coverage'], /test-coverage-lines=80/);
  assert.match(pkg.scripts['verify:nfc-coverage'], /test-coverage-branches=80/);
});
