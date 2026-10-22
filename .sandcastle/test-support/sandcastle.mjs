import { execFileSync } from 'node:child_process';
import { appendFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const root = process.env.TEST_REPO;
const record = (event) => appendFileSync(join(root, 'events.jsonl'), JSON.stringify(event) + '\n');
const git = (cwd, ...args) => execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
let iteration = 0;

export const claudeCode = () => ({});
export function docker(options) {
  record({ type: 'docker', options });
  return {};
}

export async function createSandbox(options) {
  iteration += 1;
  const worktreePath = join(root, `worktree-${iteration}`);
  git(root, 'worktree', 'add', '-b', options.branch, worktreePath);
  const change = (relativePath, text) => {
    const path = join(worktreePath, relativePath);
    mkdirSync(join(path, '..'), { recursive: true });
    writeFileSync(path, text);
  };
  return {
    branch: options.branch,
    worktreePath,
    async run({ name }) {
      record({ type: 'agent', name });
      if (iteration > 1 || process.env.TEST_NO_COMMITS === '1') {
        if (process.env.TEST_DIRTY === '1') change('uncommitted.txt', 'unfinished\n');
        return { commits: [] };
      }
      if (name === 'implementer') {
        change(process.env.TEST_NON_JAVA === '1' ? 'README.md' : 'main/manager-api/Example.java', 'implementation\n');
      } else {
        if (process.env.TEST_REBASE === '1') git(worktreePath, 'reset', '--hard', 'HEAD~2');
        change('review.txt', 'review\n');
        if (process.env.TEST_REVIEW_JAVA === '1') change('main/manager-api/Example.java', 'reviewed Java\n');
      }
      git(worktreePath, 'add', '.');
      git(worktreePath, 'commit', '-m', name);
      if (name === 'reviewer' && process.env.TEST_DIRTY === '1') change('uncommitted.txt', 'uncommitted\n');
      if (name === 'reviewer' && process.env.TEST_HOST_MOVED === '1') {
        git(root, 'commit', '--allow-empty', '-m', 'concurrent host change');
      }
      return { commits: [{ sha: git(worktreePath, 'rev-parse', 'HEAD') }] };
    },
    async exec(command) {
      record({ type: 'exec', command });
      if (command.includes('verify') && process.env.TEST_EXEC_ERROR === '1') throw new Error('container execution failed');
      if (process.env.TEST_PREFLIGHT_FAIL === '1' && command.includes('java -version')) {
        return { exitCode: 127, stdout: '', stderr: 'java unavailable' };
      }
      try {
        const stdout = execFileSync('sh', ['-c', command], {
          cwd: worktreePath,
          env: { ...process.env, PATH: `${join(root, 'bin')}:${process.env.PATH}` },
          encoding: 'utf8',
          stdio: ['ignore', 'pipe', 'pipe'],
        });
        if (command.includes('verify') && process.env.TEST_VERIFY_DIRTY === '1') change('uncommitted.txt', 'generated\n');
        if (command.includes('verify') && process.env.TEST_VERIFY_COMMIT === '1') git(worktreePath, 'commit', '--allow-empty', '-m', 'unexpected build commit');
        return { exitCode: 0, stdout, stderr: '' };
      } catch (error) {
        return { exitCode: error.status ?? 1, stdout: error.stdout ?? '', stderr: error.stderr ?? '' };
      }
    },
    async close() {
      record({ type: 'close' });
      if (process.env.TEST_CLOSE_FAIL === '1') throw new Error('cleanup failed');
    },
  };
}
