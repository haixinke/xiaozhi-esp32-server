import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const source = dirname(fileURLToPath(import.meta.url));
const git = (cwd: string, ...args: string[]) => execFileSync('git', args, {
  cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'],
}).trim();

function runFixture(flags: Record<string, string> = {}, fromConfigDir = false) {
  const root = mkdtempSync(join(tmpdir(), 'sandcastle-gate-'));
  try {
    const config = join(root, '.sandcastle');
    const pkg = join(config, 'node_modules/@ai-hero/sandcastle');
    mkdirSync(pkg, { recursive: true });
    mkdirSync(join(root, 'bin'));
    mkdirSync(join(root, 'home'));
    mkdirSync(join(root, 'main/manager-api'), { recursive: true });
    copyFileSync(join(source, 'main.mts'), join(config, 'main.mts'));
    copyFileSync(join(source, 'test-support/sandcastle.mjs'), join(pkg, 'index.mjs'));
    writeFileSync(join(pkg, 'package.json'), JSON.stringify({
      type: 'module', exports: { '.': './index.mjs', './sandboxes/docker': './index.mjs' },
    }));
    for (const tool of ['java', 'javac', 'python3.12']) {
      writeFileSync(join(root, 'bin', tool), '#!/bin/sh\nexit 0\n', { mode: 0o755 });
    }
    writeFileSync(join(root, 'bin/npm'), '#!/bin/sh\nexit 0\n', { mode: 0o755 });
    writeFileSync(join(root, 'bin/node'), '#!/bin/sh\nexit 0\n', { mode: 0o755 });
    writeFileSync(join(root, 'bin/mvn'), `#!/bin/sh
case "$*" in *--version*) exit 0 ;; esac
printf '%s\\n' "$*" >> "$TEST_REPO/maven-calls.txt"
case "$*" in *-DskipTests=false*) ;; *) exit 41 ;; esac
case "$*" in *-Dmaven.test.skip=false*) ;; *) exit 42 ;; esac
case "$*" in *-Dmaven.repo.local=/home/agent/.m2/repository*) ;; *) exit 45 ;; esac
case "$*" in *verify*) ;; *) exit 43 ;; esac
test -f main/manager-api/pom.xml || exit 44
case "$TEST_FAIL_PHASE" in
  implement) exit 1 ;;
  review) test ! -f review.txt || exit 1 ;;
esac
exit 0
`, { mode: 0o755 });
    writeFileSync(join(root, '.gitignore'), '.sandcastle/\nbin/\nhome/\nevents.jsonl\nmaven-calls.txt\nworktree-*/\n');
    writeFileSync(join(root, 'README.md'), 'fixture\n');
    writeFileSync(join(root, 'main/manager-api/pom.xml'), '<project/>\n');
    git(root, 'init', '-b', 'main');
    git(root, 'config', 'user.email', 'test@example.invalid');
    git(root, 'config', 'user.name', 'Sandcastle Test');
    git(root, 'config', 'commit.gpgsign', 'false');
    git(root, 'config', 'core.hooksPath', '/dev/null');
    git(root, 'add', '.');
    git(root, 'commit', '-m', 'initial');
    writeFileSync(join(root, 'host-baseline.txt'), 'host baseline\n');
    git(root, 'add', '.');
    git(root, 'commit', '-m', 'baseline');
    const result = spawnSync(process.execPath, ['--import', import.meta.resolve('tsx'), join(config, 'main.mts')], {
      cwd: fromConfigDir ? config : root,
      env: { ...process.env, HOME: join(root, 'home'), TEST_REPO: root, ...flags },
      encoding: 'utf8', timeout: 20_000,
    });
    const events = readFileSync(join(root, 'events.jsonl'), 'utf8').trim().split('\n').map((line) => JSON.parse(line));
    return {
      status: result.status,
      output: result.stdout + result.stderr,
      events,
      merges: git(root, 'rev-list', '--merges', 'HEAD'),
      log: git(root, 'log', '--format=%s'),
      home: join(root, 'home'),
    };
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

for (const phase of ['implement', 'review']) {
  test(`${phase} verification failure prevents automatic merge`, () => {
    const result = runFixture({ TEST_FAIL_PHASE: phase });
    assert.notEqual(result.status, 0, result.output);
    assert.equal(result.merges, '');
    assert.ok(result.events.some((event) => event.type === 'close'));
    if (phase === 'implement') assert.ok(!result.events.some((event) => event.name === 'reviewer'));
  });
}

test('successful Java changes are verified after both agents, then merged', () => {
  const result = runFixture();
  assert.equal(result.status, 0, result.output);
  assert.notEqual(result.merges, '');
  const steps = result.events.filter((event) => event.type === 'agent' || (event.type === 'exec' && event.command.includes('verify')));
  assert.deepEqual(steps.slice(0, 4).map((event) => event.name ?? 'verify'), ['implementer', 'verify', 'reviewer', 'verify']);
});

test('Maven and npm caches are mounted read-write from the host home', () => {
  const result = runFixture();
  assert.equal(result.status, 0, result.output);
  const options = result.events.find((event) => event.type === 'docker').options;
  assert.deepEqual(options?.mounts, [
    { hostPath: join(result.home, '.m2'), sandboxPath: '/home/agent/.m2', readonly: false },
    { hostPath: join(result.home, '.npm'), sandboxPath: '/home/agent/.npm', readonly: false },
  ]);
});

test('running from .sandcastle still merges into the repository root', () => {
  const result = runFixture({}, true);
  assert.equal(result.status, 0, result.output);
  assert.notEqual(result.merges, '');
});

test('Java introduced by review is verified even when implementation only changed docs', () => {
  const result = runFixture({ TEST_NON_JAVA: '1', TEST_REVIEW_JAVA: '1', TEST_FAIL_PHASE: 'review' });
  assert.notEqual(result.status, 0, result.output);
  assert.equal(result.merges, '');
  assert.ok(result.events.some((event) => event.type === 'exec' && event.command.includes('verify')));
});

test('non-Java changes do not require Maven verification', () => {
  const result = runFixture({ TEST_NON_JAVA: '1', TEST_FAIL_PHASE: 'implement' });
  assert.equal(result.status, 0, result.output);
  assert.notEqual(result.merges, '');
  assert.ok(!result.events.some((event) => event.type === 'exec' && event.command.includes('verify')));
});

// 多子项目门禁：变更落在哪个 prefix 就跑哪个 check，失败则拦截 merge。
for (const [subproject, marker] of [
  ['main/manager-web/', 'npm run test:unit'],
  ['main/egg-miniprogram/', 'verify-project.js'],
  ['main/xiaozhi-server/', 'compileall'],
] as const) {
  test(`${subproject} changes trigger the matching check and pass`, () => {
    const result = runFixture({ TEST_SUBPROJECT: subproject });
    assert.equal(result.status, 0, result.output);
    assert.notEqual(result.merges, '');
    const checkExecs = result.events.filter((event) => event.type === 'exec' && event.command.includes(marker));
    assert.equal(checkExecs.length, 2, `expected ${marker} to run after implement and after review`);
  });

  test(`${subproject} check failure blocks merge`, () => {
    const failKey = subproject.replace(/^main\//, '').replace(/\/$/, '');
    const result = runFixture({ TEST_SUBPROJECT: subproject, TEST_FAIL_SUBPROJECT: failKey });
    assert.notEqual(result.status, 0, result.output);
    assert.equal(result.merges, '');
  });
}

for (const flag of ['TEST_DIRTY', 'TEST_HOST_MOVED', 'TEST_CLOSE_FAIL', 'TEST_PREFLIGHT_FAIL', 'TEST_EXEC_ERROR', 'TEST_VERIFY_DIRTY', 'TEST_VERIFY_COMMIT', 'TEST_REBASE']) {
  test(`${flag} prevents merge`, () => {
    const result = runFixture({ [flag]: '1' });
    assert.notEqual(result.status, 0, result.output);
    assert.equal(result.merges, '');
  });
}

test('an implementation without commits stops without reviewing or merging', () => {
  const result = runFixture({ TEST_NO_COMMITS: '1' });
  assert.equal(result.status, 0, result.output);
  assert.equal(result.merges, '');
  assert.ok(!result.events.some((event) => event.name === 'reviewer'));
});

test('uncommitted implementation is reported as failure rather than an empty backlog', () => {
  const result = runFixture({ TEST_NO_COMMITS: '1', TEST_DIRTY: '1' });
  assert.notEqual(result.status, 0, result.output);
  assert.equal(result.merges, '');
});
